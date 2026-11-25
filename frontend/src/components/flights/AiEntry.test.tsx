import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { json, mockApi, problem } from "@/test/mock-api";
import { NewFlight } from "./NewFlight";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, back: vi.fn(), replace: vi.fn() }) }));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const outbound = {
  flightNumber: "NH242", flightDate: "2026-11-03", depIata: "FUK", arrIata: "HND",
  aircraftType: "B789", registration: null, notes: "窗边", warnings: [],
};
const inbound = {
  flightNumber: "NH241", flightDate: null, depIata: "HND", arrIata: "FUK",
  aircraftType: null, registration: null, notes: null, warnings: ["没有识别出日期"],
};

beforeAll(() => {
  // jsdom 没有 createObjectURL（图片预览用）
  URL.createObjectURL = vi.fn(() => "blob:preview");
  URL.revokeObjectURL = vi.fn();
});

describe("AI 解析录入", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    push.mockReset();
  });

  it("解析出往返两段，逐条预填表单、确认保存，全部完成后回到列表", async () => {
    const api = mockApi({
      "GET /api/config": () => json(200, { aiEnabled: true, lookupEnabled: false }),
      "POST /api/ai/parse": () => json(200, { flights: [outbound, inbound] }),
      "POST /api/flights": () => json(201, { id: 1 }),
    });
    render(<NewFlight />, { wrapper });

    await userEvent.click(await screen.findByRole("tab", { name: "AI 解析" }));
    await userEvent.type(screen.getByLabelText("粘贴文字"), "11月3日 NH242 福冈到羽田，6号回来");
    await userEvent.click(screen.getByRole("button", { name: "解析文字" }));

    const results = await screen.findByRole("region", { name: "解析结果" });
    expect(within(results).getAllByRole("listitem")).toHaveLength(2);
    expect(results).toHaveTextContent("没有识别出日期");
    await userEvent.click(within(results).getByRole("button", { name: "逐条确认（2 条）" }));

    // 第 1 条
    expect(screen.getByText(/第 1 \/ 2 条/)).toBeInTheDocument();
    expect(screen.getByLabelText("航班号")).toHaveValue("NH242");
    expect(screen.getByLabelText("备注")).toHaveValue("窗边");
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    // 第 2 条，带提示
    expect(await screen.findByText(/第 2 \/ 2 条/)).toBeInTheDocument();
    expect(screen.getByLabelText("航班号")).toHaveValue("NH241");
    expect(screen.getByText(/没有识别出日期/)).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(api.calls("POST", "/api/flights")).toHaveLength(2);
    expect(api.calls("POST", "/api/flights")[0]).toMatchObject({ source: "ai", flightNumber: "NH242" });
    expect(push).toHaveBeenCalledWith("/flights");
  });

  it("可以跳过某一条", async () => {
    const api = mockApi({
      "GET /api/config": () => json(200, { aiEnabled: true, lookupEnabled: false }),
      "POST /api/ai/parse": () => json(200, { flights: [outbound, inbound] }),
      "POST /api/flights": () => json(201, { id: 1 }),
    });
    render(<NewFlight />, { wrapper });

    await userEvent.click(await screen.findByRole("tab", { name: "AI 解析" }));
    await userEvent.type(screen.getByLabelText("粘贴文字"), "x");
    await userEvent.click(screen.getByRole("button", { name: "解析文字" }));
    await userEvent.click(await screen.findByRole("button", { name: "逐条确认（2 条）" }));

    await userEvent.click(screen.getByRole("button", { name: "跳过这条" }));
    expect(screen.getByLabelText("航班号")).toHaveValue("NH241");
    await userEvent.click(screen.getByRole("button", { name: "跳过这条" }));
    expect(api.calls("POST", "/api/flights")).toHaveLength(0);
    expect(push).toHaveBeenCalledWith("/flights");
  });

  it("上传截图识别：拍照入口用后置摄像头，图片以表单数据发送", async () => {
    const api = mockApi({
      "GET /api/config": () => json(200, { aiEnabled: true, lookupEnabled: false }),
      "POST /api/ai/parse": () => json(200, { flights: [outbound] }),
    });
    render(<NewFlight />, { wrapper });

    await userEvent.click(await screen.findByRole("tab", { name: "AI 解析" }));
    expect(screen.getByLabelText("拍照")).toHaveAttribute("capture", "environment");
    expect(screen.getByLabelText("拍照")).toHaveAttribute("accept", "image/*");

    const file = new File([new Uint8Array([1, 2, 3])], "pass.png", { type: "image/png" });
    await userEvent.upload(screen.getByLabelText("选择截图"), file);
    expect(screen.getByRole("img", { name: "要识别的图片" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "识别图片" }));

    await screen.findByRole("region", { name: "解析结果" });
    const [, init] = api.fetchMock.mock.calls.find(([url]) => String(url) === "/api/ai/parse")!;
    expect(init?.body).toBeInstanceOf(FormData);
    expect((init?.body as FormData).get("image")).toBeInstanceOf(File);
  });

  it("额度用完时显示中文提示", async () => {
    mockApi({
      "GET /api/config": () => json(200, { aiEnabled: true, lookupEnabled: false }),
      "POST /api/ai/parse": () => problem(429, { detail: "今天的免费额度用完了，请手动填写或稍后再试" }),
    });
    render(<NewFlight />, { wrapper });

    await userEvent.click(await screen.findByRole("tab", { name: "AI 解析" }));
    await userEvent.type(screen.getByLabelText("粘贴文字"), "x");
    await userEvent.click(screen.getByRole("button", { name: "解析文字" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("今天的免费额度用完了");
  });
});
