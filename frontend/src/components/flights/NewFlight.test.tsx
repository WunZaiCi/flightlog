import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { json, mockApi, problem } from "@/test/mock-api";
import { NewFlight } from "./NewFlight";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, back: vi.fn(), replace: vi.fn() }) }));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const candidate = {
  flightNumber: "NH242",
  flightDate: "2026-11-03",
  depIata: "FUK",
  arrIata: "HND",
  depName: "Fukuoka",
  arrName: "Tokyo Haneda",
  aircraftType: "B789",
  aircraftModel: "Boeing 787-9",
  registration: "JA891A",
  airlineName: "ANA",
};

async function lookup(number = "NH242", date = "2026-11-03") {
  await userEvent.click(await screen.findByRole("tab", { name: "航班号查询" }));
  const panel = screen.getByRole("tabpanel");
  await userEvent.type(within(panel).getByLabelText("航班号"), number);
  const dateInput = within(panel).getByLabelText("日期");
  await userEvent.clear(dateInput);
  await userEvent.type(dateInput, date);
  await userEvent.click(within(panel).getByRole("button", { name: "查询" }));
}

describe("NewFlight", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    push.mockReset();
  });

  it("航班号查询后预填表单，确认后以 api 来源保存", async () => {
    const api = mockApi({
      "GET /api/config": () => json(200, { aiEnabled: false, lookupEnabled: true }),
      "GET /api/lookup/flight": () => json(200, { cached: false, candidates: [candidate] }),
      "POST /api/flights": () => json(201, { id: 5 }),
    });
    render(<NewFlight />, { wrapper });

    await lookup();
    const result = await screen.findByRole("region", { name: "查询结果" });
    expect(result).toHaveTextContent("FUK → HND");
    expect(result).toHaveTextContent("Boeing 787-9");
    await userEvent.click(within(result).getByRole("button", { name: "使用这条" }));

    // 预填到表单，用户确认后保存
    expect(screen.getByLabelText("航班号")).toHaveValue("NH242");
    expect(screen.getByRole("combobox", { name: "出发机场" })).toHaveValue("FUK");
    expect(screen.getByRole("combobox", { name: "机型" })).toHaveValue("B789");
    expect(screen.getByLabelText("注册号")).toHaveValue("JA891A");
    expect(api.calls("POST", "/api/flights")).toHaveLength(0);

    await userEvent.click(screen.getByRole("button", { name: "保存" }));
    expect(api.calls("POST", "/api/flights")[0]).toMatchObject({ source: "api", flightNumber: "NH242", depIata: "FUK" });
    expect(push).toHaveBeenCalledWith("/flights");
  });

  it("接口返回的机型匹配不到代码时提示手动选择", async () => {
    mockApi({
      "GET /api/config": () => json(200, { aiEnabled: false, lookupEnabled: true }),
      "GET /api/lookup/flight": () =>
        json(200, { cached: true, candidates: [{ ...candidate, aircraftType: null, aircraftModel: "Comac C939" }] }),
    });
    render(<NewFlight />, { wrapper });

    await lookup();
    await userEvent.click(await screen.findByRole("button", { name: "使用这条" }));
    expect(screen.getByText(/Comac C939/)).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "机型" })).toHaveValue("");
  });

  it("接口只返回系列时，机型框预填系列名并展开具体型号让用户选", async () => {
    mockApi({
      "GET /api/config": () => json(200, { aiEnabled: false, lookupEnabled: true }),
      "GET /api/lookup/flight": () =>
        json(200, { cached: false, candidates: [{ ...candidate, aircraftType: null, aircraftModel: "Boeing 777", aircraftSearch: "777" }] }),
      "GET /api/ref/aircraft-types": () =>
        json(200, [
          { icaoCode: "B772", manufacturer: "Boeing", family: "777", model: "777-200", displayName: "Boeing 777-200", body: "wide" },
          { icaoCode: "B77W", manufacturer: "Boeing", family: "777", model: "777-300ER", displayName: "Boeing 777-300ER", body: "wide" },
        ]),
    });
    render(<NewFlight />, { wrapper });

    await lookup();
    await userEvent.click(await screen.findByRole("button", { name: "使用这条" }));

    expect(screen.getByText(/只返回了系列「Boeing 777」/)).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "机型" })).toHaveValue("777");
    const list = await screen.findByRole("listbox", { name: "机型" });
    // 只提示选择具体型号，不会同时说「未识别机型、仍可保存」
    expect(screen.getByText("请从下拉框里选择具体机型")).toBeInTheDocument();
    expect(screen.queryByText(/未识别机型/)).not.toBeInTheDocument();
    await userEvent.click(within(list).getByRole("option", { name: /777-300ER/ }));
    expect(screen.getByRole("combobox", { name: "机型" })).toHaveValue("B77W");
  });

  it("额度用完时显示中文提示", async () => {
    mockApi({
      "GET /api/config": () => json(200, { aiEnabled: false, lookupEnabled: true }),
      "GET /api/lookup/flight": () => problem(429, { detail: "今天的免费额度用完了，请手动填写或稍后再试" }),
    });
    render(<NewFlight />, { wrapper });

    await lookup();
    expect(await screen.findByRole("alert")).toHaveTextContent("今天的免费额度用完了，请手动填写或稍后再试");
  });

  it("没有配置 key 时查询不可用，手动填写照常", async () => {
    mockApi({ "GET /api/config": () => json(200, { aiEnabled: false, lookupEnabled: false }) });
    render(<NewFlight />, { wrapper });

    await userEvent.click(await screen.findByRole("tab", { name: "航班号查询" }));
    const panel = screen.getByRole("tabpanel");
    expect(panel).toHaveTextContent("没有配置 AeroDataBox API key");
    expect(within(panel).queryByRole("button", { name: "查询" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "手动填写" }));
    expect(screen.getByLabelText("航班号")).toBeInTheDocument();
  });
});
