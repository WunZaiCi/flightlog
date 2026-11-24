import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { RoutesData } from "@/lib/routes";
import { flight } from "@/test/fixtures";
import { json, mockApi } from "@/test/mock-api";
import { MapPage } from "./MapPage";

// MapLibre 需要 WebGL，jsdom 里用替身：暴露选中航线和机场的按钮
vi.mock("./RouteMap", () => ({
  RouteMap: (p: { onSelectRoute: (k: string) => void; onSelectAirport: (i: string) => void }) => (
    <div data-testid="route-map">
      <button onClick={() => p.onSelectAirport("HND")}>地图上的 HND</button>
    </div>
  ),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const HND = { iata: "HND", name: "Tokyo Haneda International Airport", city: "Tokyo", countryCode: "JP", lat: 35.55, lon: 139.78 };
const data: RoutesData = {
  routes: [
    {
      from: "FUK", to: "HND", count: 2, distanceKm: 882,
      flights: [
        { id: 2, flightDate: "2025-03-05", flightNumber: "NH242", depIata: "FUK", arrIata: "HND", airlineName: "All Nippon Airways", aircraft: "Boeing 787-9" },
        { id: 1, flightDate: "2025-03-01", flightNumber: "NH241", depIata: "HND", arrIata: "FUK", airlineName: "All Nippon Airways", aircraft: "Boeing 787-9" },
      ],
    },
    { from: "NRT", to: "SIN", count: 1, distanceKm: 5330, flights: [{ id: 3, flightDate: "2026-01-10", flightNumber: "SQ12", depIata: "SIN", arrIata: "NRT", airlineName: "Singapore Airlines", aircraft: "Airbus A380-800" }] },
  ],
  airports: [
    { ...HND, visits: 2, departures: 1, arrivals: 1 },
    { iata: "FUK", name: "Fukuoka Airport", city: "Fukuoka", countryCode: "JP", lat: 33.58, lon: 130.45, visits: 2, departures: 1, arrivals: 1 },
  ],
  totals: {
    flightCount: 3, totalDistanceKm: 7094, countryCount: 2,
    longest: { from: "NRT", to: "SIN", distanceKm: 5330 },
    shortest: { from: "FUK", to: "HND", distanceKm: 882 },
  },
};

function routes() {
  return {
    "GET /api/stats/routes": () => json(200, data),
    "GET /api/flights": () => json(200, [flight({ id: 1, flightDate: "2025-03-01" }), flight({ id: 3, flightDate: "2026-01-10" })]),
  };
}

describe("MapPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("侧栏显示总里程、绕地球圈数、国家和地区数、最长和最短航线", async () => {
    mockApi(routes());
    render(<MapPage />, { wrapper });

    const summary = await screen.findByRole("region", { name: "航线统计" });
    expect(summary).toHaveTextContent("7,094 km");
    expect(summary).toHaveTextContent("绕地球 0.18 圈");
    expect(summary).toHaveTextContent("国家和地区2");
    expect(summary).toHaveTextContent("最长NRT ⇄ SIN · 5,330 km");
    expect(summary).toHaveTextContent("最短FUK ⇄ HND · 882 km");
  });

  it("从航线列表选择航线，显示这条航线的所有航班", async () => {
    mockApi(routes());
    render(<MapPage />, { wrapper });

    const list = await screen.findByRole("region", { name: "航线" });
    await userEvent.click(within(list).getByRole("button", { name: /FUK ⇄ HND/ }));

    const detail = screen.getByRole("region", { name: "FUK ⇄ HND" });
    expect(within(detail).getAllByRole("listitem")).toHaveLength(2);
    expect(detail).toHaveTextContent("NH242");
    expect(within(detail).getAllByRole("link", { name: /编辑/ })[0]).toHaveAttribute("href", "/flights/edit?id=2");
  });

  it("点地图上的机场显示机场统计", async () => {
    mockApi(routes());
    render(<MapPage />, { wrapper });

    await userEvent.click(await screen.findByRole("button", { name: "地图上的 HND" }));
    const detail = screen.getByRole("region", { name: /HND/ });
    expect(detail).toHaveTextContent("Tokyo Haneda International Airport");
    expect(detail).toHaveTextContent("到访2");
    expect(detail).toHaveTextContent("FUK ⇄ HND");
  });

  it("筛选条件带到接口参数里", async () => {
    const api = mockApi(routes());
    render(<MapPage />, { wrapper });

    await screen.findByRole("region", { name: "航线统计" });
    await userEvent.selectOptions(screen.getByLabelText("年份"), "2026");

    await vi.waitFor(() =>
      expect(api.fetchMock).toHaveBeenCalledWith("/api/stats/routes?year=2026", expect.anything()),
    );
  });

  it("没有航班时提示", async () => {
    mockApi({
      "GET /api/stats/routes": () =>
        json(200, { routes: [], airports: [], totals: { flightCount: 0, totalDistanceKm: 0, countryCount: 0, longest: null, shortest: null } }),
      "GET /api/flights": () => json(200, []),
    });
    render(<MapPage />, { wrapper });
    expect(await screen.findByText("还没有航班，录入后会在地图上画出航线")).toBeInTheDocument();
  });
});
