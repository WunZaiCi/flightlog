import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { json, mockApi, problem } from "@/test/mock-api";
import { FlightForm } from "./FlightForm";

const FUK = { iata: "FUK", icao: "RJFF", name: "Fukuoka Airport", city: "Fukuoka", countryCode: "JP", lat: 33.58, lon: 130.45 };
const HND = { iata: "HND", icao: "RJTT", name: "Tokyo Haneda International Airport", city: "Tokyo", countryCode: "JP", lat: 35.55, lon: 139.78 };
const B789 = { icaoCode: "B789", manufacturer: "Boeing", family: "787", model: "787-9", displayName: "Boeing 787-9", body: "wide" };

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function refRoutes() {
  return {
    "GET /api/ref/airports": (url: URL) => {
      const q = (url.searchParams.get("q") ?? "").toUpperCase();
      return json(200, [FUK, HND].filter((a) => a.iata.startsWith(q) || a.city.toUpperCase().startsWith(q)));
    },
    "GET /api/ref/aircraft-types": (url: URL) => {
      const q = url.searchParams.get("q") ?? "";
      return json(200, q && "787-9 B789".includes(q.toUpperCase()) ? [B789] : []);
    },
  };
}

async function pickOption(label: RegExp | string, text: string, option: RegExp) {
  const input = screen.getByRole("combobox", { name: label });
  await userEvent.clear(input);
  await userEvent.type(input, text);
  const listbox = await screen.findByRole("listbox");
  await userEvent.click(await within(listbox).findByRole("option", { name: option }));
}

async function fillRequired() {
  await userEvent.clear(screen.getByLabelText("日期"));
  await userEvent.type(screen.getByLabelText("日期"), "2026-11-03");
  await userEvent.type(screen.getByLabelText("航班号"), "nh 242");
  await pickOption("出发机场", "fuk", /FUK/);
  await pickOption("到达机场", "hnd", /HND/);
}

describe("FlightForm", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("从自动补全选择机场和机型后提交", async () => {
    const api = mockApi({
      ...refRoutes(),
      "POST /api/flights": () => json(201, { id: 7 }),
    });
    const onSaved = vi.fn();
    render(<FlightForm onSaved={onSaved} />, { wrapper });

    await fillRequired();
    expect(screen.getByText(/Fukuoka Airport/)).toBeInTheDocument();
    await pickOption("机型", "787-9", /Boeing 787-9/);
    await userEvent.type(screen.getByLabelText("注册号"), "ja891a");
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(onSaved).toHaveBeenCalledWith({ id: 7 });
    expect(api.calls("POST", "/api/flights")[0]).toEqual({
      flightDate: "2026-11-03",
      flightNumber: "nh 242",
      depIata: "FUK",
      arrIata: "HND",
      aircraftType: "B789",
      registration: "ja891a",
      notes: null,
      source: "manual",
      allowDuplicate: false,
    });
  });

  it("键盘可以选择自动补全选项", async () => {
    mockApi(refRoutes());
    render(<FlightForm onSaved={vi.fn()} />, { wrapper });

    const input = screen.getByRole("combobox", { name: "出发机场" });
    await userEvent.type(input, "fuk");
    await screen.findByRole("option", { name: /FUK/ });
    await userEvent.keyboard("{ArrowDown}{Enter}");

    expect(input).toHaveValue("FUK");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("服务器返回的字段错误显示在对应输入框下", async () => {
    mockApi({
      ...refRoutes(),
      "POST /api/flights": () =>
        problem(400, { detail: "找不到机场 ZZZ", errors: { depIata: "找不到机场 ZZZ" } }),
    });
    render(<FlightForm onSaved={vi.fn()} />, { wrapper });

    await fillRequired();
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(await screen.findByText("找不到机场 ZZZ", { selector: "p[id$=-error]" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "出发机场" })).toHaveAttribute("aria-invalid", "true");
  });

  it("可能重复时提示，确认后带 allowDuplicate 重新提交", async () => {
    let attempts = 0;
    const api = mockApi({
      ...refRoutes(),
      "POST /api/flights": () =>
        ++attempts === 1
          ? problem(409, { detail: "这一天已经录入过 NH242，可能是重复录入", duplicateIds: [3] })
          : json(201, { id: 8 }),
    });
    const onSaved = vi.fn();
    render(<FlightForm onSaved={onSaved} />, { wrapper });

    await fillRequired();
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    const warning = await screen.findByRole("alert");
    expect(warning).toHaveTextContent("这一天已经录入过 NH242，可能是重复录入");
    await userEvent.click(within(warning).getByRole("button", { name: "仍然保存" }));

    expect(onSaved).toHaveBeenCalledWith({ id: 8 });
    expect(api.calls("POST", "/api/flights")[1]).toMatchObject({ allowDuplicate: true });
  });

  it("机型不在列表里时提示会标记为未识别，但仍可保存", async () => {
    const api = mockApi({ ...refRoutes(), "POST /api/flights": () => json(201, { id: 9 }) });
    render(<FlightForm onSaved={vi.fn()} />, { wrapper });

    await fillRequired();
    await userEvent.type(screen.getByRole("combobox", { name: "机型" }), "x9z");

    expect(await screen.findByText(/未识别机型/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "保存" }));
    expect(api.calls("POST", "/api/flights")[0]).toMatchObject({ aircraftType: "X9Z" });
  });

  it("编辑时显示已有数据，并用 onSubmit 提交", async () => {
    mockApi(refRoutes());
    const submit = vi.fn().mockResolvedValue({ id: 5 });
    render(
      <FlightForm
        onSaved={vi.fn()}
        submit={submit}
        initial={{
          flightDate: "2025-08-01",
          flightNumber: "NH241",
          depIata: "HND",
          arrIata: "FUK",
          aircraftType: "B789",
          registration: "JA891A",
          notes: "靠窗",
        }}
      />,
      { wrapper },
    );

    expect(screen.getByLabelText("航班号")).toHaveValue("NH241");
    expect(screen.getByLabelText("备注")).toHaveValue("靠窗");
    await userEvent.click(screen.getByRole("button", { name: "保存" }));
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ flightNumber: "NH241", depIata: "HND" }));
  });
});
