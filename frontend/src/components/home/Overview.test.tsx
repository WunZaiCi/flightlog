import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { flight } from "@/test/fixtures";
import { json, mockApi } from "@/test/mock-api";
import { Overview } from "./Overview";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const summary = {
  flightCount: 12,
  aircraftTypeCount: 7,
  unrecognizedTypeCount: 1,
  airlineCount: 4,
  airportCount: 9,
  totalDistanceKm: 23456,
  recentFlights: [flight({ id: 1 }), flight({ id: 2, flightNumber: "JL305", flightDate: "2026-10-01" })],
  newlyUnlocked: [
    { icaoCode: "A359", displayName: "Airbus A350-900", recognized: true, firstFlightDate: "2026-10-01", firstFlightNumber: "JL305" },
    { icaoCode: "X9Z", displayName: null, recognized: false, firstFlightDate: "2026-09-20", firstFlightNumber: "MU5101" },
  ],
};

describe("Overview", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("显示总数、总里程和绕地球圈数", async () => {
    mockApi({ "GET /api/stats/summary": () => json(200, summary) });
    render(<Overview />, { wrapper });

    const stats = await screen.findByRole("list", { name: "统计" });
    const tile = (label: string) => within(stats).getByText(label).closest("li")!;
    expect(tile("航班")).toHaveTextContent("12");
    expect(tile("机型")).toHaveTextContent("7");
    expect(tile("机型")).toHaveTextContent("1 个未识别");
    expect(tile("航司")).toHaveTextContent("4");
    expect(tile("机场")).toHaveTextContent("9");
    expect(tile("总里程")).toHaveTextContent("23,456 km");
    expect(tile("总里程")).toHaveTextContent("绕地球 0.59 圈");
  });

  it("显示最近的航班和新解锁机型", async () => {
    mockApi({ "GET /api/stats/summary": () => json(200, summary) });
    render(<Overview />, { wrapper });

    const recent = await screen.findByRole("region", { name: "最近的航班" });
    expect(within(recent).getAllByRole("listitem")).toHaveLength(2);
    expect(within(recent).getByText("JL305")).toBeInTheDocument();

    const unlocked = screen.getByRole("region", { name: "新解锁的机型" });
    expect(within(unlocked).getByText("Airbus A350-900")).toBeInTheDocument();
    expect(within(unlocked).getByText("X9Z（未识别）")).toBeInTheDocument();
    expect(within(unlocked).getAllByText("新解锁")).toHaveLength(2);
  });

  it("没有航班时显示引导", async () => {
    mockApi({
      "GET /api/stats/summary": () =>
        json(200, { ...summary, flightCount: 0, totalDistanceKm: 0, recentFlights: [], newlyUnlocked: [] }),
    });
    render(<Overview />, { wrapper });

    expect(await screen.findByText("还没有航班记录")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "录入第一个航班" })).toHaveAttribute("href", "/flights/new");
  });

  it("最近 90 天没有新机型时提示", async () => {
    mockApi({ "GET /api/stats/summary": () => json(200, { ...summary, newlyUnlocked: [] }) });
    render(<Overview />, { wrapper });
    expect(await screen.findByText("最近 90 天没有坐到新机型")).toBeInTheDocument();
  });
});
