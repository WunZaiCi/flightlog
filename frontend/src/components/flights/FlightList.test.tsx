import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { flight } from "@/test/fixtures";
import { json, mockApi } from "@/test/mock-api";
import { FlightList } from "./FlightList";


function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("FlightList", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("显示航班的日期、航线、机型和距离", () => {
    render(<FlightList flights={[flight()]} />, { wrapper });
    const item = screen.getByRole("listitem");
    expect(item).toHaveTextContent("2026-11-03");
    expect(item).toHaveTextContent("NH242");
    expect(item).toHaveTextContent("FUK");
    expect(item).toHaveTextContent("HND");
    expect(item).toHaveTextContent("Boeing 787-9");
    expect(item).toHaveTextContent("JA891A");
    expect(item).toHaveTextContent("880 km");
    expect(within(item).getByRole("link", { name: "编辑 NH242" })).toHaveAttribute("href", "/flights/edit?id=1");
  });

  it("未识别机型显示提示", () => {
    render(
      <FlightList flights={[flight({ aircraftTypeIcao: "X9Z", aircraftTypeRecognized: false, aircraftType: null })]} />,
      { wrapper },
    );
    expect(screen.getByText("未识别机型 X9Z")).toBeInTheDocument();
  });

  it("删除前要确认", async () => {
    const api = mockApi({ "DELETE /api/flights/1": () => new Response(null, { status: 204 }), "GET /api/flights": () => json(200, []) });
    render(<FlightList flights={[flight()]} />, { wrapper });

    await userEvent.click(screen.getByRole("button", { name: "删除 NH242" }));
    expect(api.fetchMock).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "确认删除" }));

    expect(api.fetchMock).toHaveBeenCalledWith("/api/flights/1", expect.objectContaining({ method: "DELETE" }));
  });

  it("没有航班时显示提示和录入入口", () => {
    render(<FlightList flights={[]} />, { wrapper });
    expect(screen.getByText("还没有航班记录")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "录入第一个航班" })).toHaveAttribute("href", "/flights/new");
  });
});
