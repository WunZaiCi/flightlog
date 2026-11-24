import { describe, expect, it } from "vitest";
import { flight } from "@/test/fixtures";
import { activeFilterCount, facets, parseFilters, toQueryString } from "./flight-filters";

describe("flight-filters", () => {
  it("从 URL 参数解析筛选条件，忽略空值和未知参数", () => {
    const params = new URLSearchParams("year=2025&airline=&type=B789&foo=1&q=%E4%B8%9C%E4%BA%AC");
    expect(parseFilters(params)).toEqual({ year: "2025", type: "B789", q: "东京" });
  });

  it("生成查询字符串，跳过空值，顺序固定", () => {
    expect(toQueryString({ q: "nh", year: "2025", airline: "" })).toBe("year=2025&q=nh");
    expect(toQueryString({})).toBe("");
  });

  it("统计已启用的筛选条件数量", () => {
    expect(activeFilterCount({ year: "2025", q: "x" })).toBe(2);
    expect(activeFilterCount({})).toBe(0);
  });

  it("从航班列表生成筛选选项：年份倒序，其他按次数排序", () => {
    const flights = [
      flight({ id: 1, flightDate: "2025-01-01" }),
      flight({ id: 2, flightDate: "2026-05-01", flightNumber: "JL305", airlineIata: "JL", airlineName: "Japan Airlines" }),
      flight({ id: 3, flightDate: "2025-06-01" }),
      flight({ id: 4, flightDate: "2024-06-01", aircraftTypeIcao: null, aircraftType: null, aircraftTypeRecognized: false }),
    ];

    const f = facets(flights);
    expect(f.years).toEqual(["2026", "2025", "2024"]);
    expect(f.airlines).toEqual([
      { value: "NH", label: "NH · All Nippon Airways", count: 3 },
      { value: "JL", label: "JL · Japan Airlines", count: 1 },
    ]);
    expect(f.types).toEqual([{ value: "B789", label: "Boeing 787-9", count: 3 }]);
    expect(f.airports.map((a) => a.value)).toEqual(["FUK", "HND"]);
    expect(f.airports[0]).toEqual({ value: "FUK", label: "FUK · Fukuoka", count: 4 });
  });
});
