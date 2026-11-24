import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AircraftStats } from "@/lib/aircraft-stats";
import { json, mockApi } from "@/test/mock-api";
import { AircraftPage } from "./AircraftPage";

// jsdom 不能渲染 ECharts，只检查传给图表的说明文字
vi.mock("@/components/charts/EChart", () => ({
  EChart: ({ label }: { label: string }) => <div role="img" aria-label={label} />,
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const stats: AircraftStats = {
  types: [
    {
      icaoCode: "B789", manufacturer: "Boeing", family: "787", model: "787-9", displayName: "Boeing 787-9", body: "wide",
      recognized: true, flightCount: 3, distanceKm: 2196, firstFlightDate: "2024-05-01", firstFlightNumber: "NH241", isNew: false,
      airlines: [{ iata: "NH", name: "All Nippon Airways", count: 2 }, { iata: "JL", name: "Japan Airlines", count: 1 }],
      registrations: [{ registration: "JA891A", count: 2 }, { registration: "JA861J", count: 1 }],
    },
    {
      icaoCode: "A359", manufacturer: "Airbus", family: "A350", model: "A350-900", displayName: "Airbus A350-900", body: "wide",
      recognized: true, flightCount: 1, distanceKm: 432, firstFlightDate: "2026-11-20", firstFlightNumber: "JL306", isNew: true,
      airlines: [], registrations: [],
    },
    {
      icaoCode: "X9Z", manufacturer: null, family: null, model: null, displayName: null, body: null,
      recognized: false, flightCount: 1, distanceKm: 1080, firstFlightDate: "2025-07-01", firstFlightNumber: "MU5101", isNew: false,
      airlines: [], registrations: [],
    },
  ],
  bodyShare: { narrow: 0, wide: 4, regional: 0, unknown: 2 },
  byYear: [{ year: "2024", narrow: 0, wide: 1, regional: 0, unknown: 0 }, { year: "2025", narrow: 0, wide: 3, regional: 0, unknown: 2 }],
};

describe("AircraftPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("收集墙：每个机型一张卡片，新解锁的有徽章", async () => {
    mockApi({ "GET /api/stats/aircraft": () => json(200, stats) });
    render(<AircraftPage />, { wrapper });

    const wall = await screen.findByRole("region", { name: "机型收集墙" });
    const cards = within(wall).getAllByRole("button");
    expect(cards).toHaveLength(3);
    expect(cards[0]).toHaveTextContent("Boeing 787-9");
    expect(cards[0]).toHaveTextContent("3 次");
    expect(cards[0]).toHaveTextContent("2024-05-01 · NH241");
    expect(within(cards[1]).getByText("新解锁")).toBeInTheDocument();
    expect(within(cards[0]).queryByText("新解锁")).not.toBeInTheDocument();
    expect(cards[2]).toHaveTextContent("X9Z（未识别）");
  });

  it("提示未识别机型，并能跳到对应航班", async () => {
    mockApi({ "GET /api/stats/aircraft": () => json(200, stats) });
    render(<AircraftPage />, { wrapper });

    const notice = await screen.findByRole("note");
    expect(notice).toHaveTextContent("1 个机型不在机型表里");
    expect(within(notice).getByRole("link", { name: "X9Z" })).toHaveAttribute("href", "/flights?type=X9Z");
  });

  it("点击机型显示详情：次数、里程、航司、注册号", async () => {
    mockApi({ "GET /api/stats/aircraft": () => json(200, stats) });
    render(<AircraftPage />, { wrapper });

    const wall = await screen.findByRole("region", { name: "机型收集墙" });
    await userEvent.click(within(wall).getAllByRole("button")[0]);

    const dialog = screen.getByRole("dialog", { name: "Boeing 787-9" });
    expect(dialog).toHaveTextContent("乘坐次数3");
    expect(dialog).toHaveTextContent("2,196 km");
    expect(dialog).toHaveTextContent("All Nippon Airways");
    expect(within(dialog).getByText("JA891A").closest("li")).toHaveTextContent("2 次");

    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("分层统计可以展开到具体型号", async () => {
    mockApi({ "GET /api/stats/aircraft": () => json(200, stats) });
    render(<AircraftPage />, { wrapper });

    const tree = await screen.findByRole("region", { name: "按制造商" });
    expect(within(tree).getByText("Boeing").closest("summary")).toHaveTextContent("3 次");
    expect(within(tree).getByText("未识别机型")).toBeInTheDocument();
    expect(within(tree).getByText("787").closest("summary")).toHaveTextContent("3 次");
  });

  it("窄体宽体占比用分段条，带图例和次数", async () => {
    mockApi({ "GET /api/stats/aircraft": () => json(200, stats) });
    render(<AircraftPage />, { wrapper });

    const share = await screen.findByRole("region", { name: "窄体机和宽体机占比" });
    const legend = within(share).getByRole("list");
    expect(legend).toHaveTextContent("宽体 4 次 · 67%");
    expect(legend).toHaveTextContent("未知 2 次 · 33%");
    expect(legend).not.toHaveTextContent("窄体");
  });

  it("图表旁有数据表", async () => {
    mockApi({ "GET /api/stats/aircraft": () => json(200, stats) });
    render(<AircraftPage />, { wrapper });

    expect(await screen.findByRole("img", { name: /机型排行榜/ })).toBeInTheDocument();
    const trend = screen.getByRole("region", { name: "按年份的变化" });
    const table = within(trend).getByRole("table");
    expect(within(table).getAllByRole("row")).toHaveLength(3);
  });

  it("没有机型数据时提示", async () => {
    mockApi({
      "GET /api/stats/aircraft": () =>
        json(200, { types: [], bodyShare: { narrow: 0, wide: 0, regional: 0, unknown: 0 }, byYear: [] }),
    });
    render(<AircraftPage />, { wrapper });
    expect(await screen.findByText("还没有填写机型的航班")).toBeInTheDocument();
  });
});
