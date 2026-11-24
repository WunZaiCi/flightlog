import { describe, expect, it } from "vitest";
import { buildHierarchy, shareSegments, type AircraftTypeStat } from "./aircraft-stats";

function stat(o: Partial<AircraftTypeStat>): AircraftTypeStat {
  return {
    icaoCode: "B789",
    manufacturer: "Boeing",
    family: "787",
    model: "787-9",
    displayName: "Boeing 787-9",
    body: "wide",
    recognized: true,
    flightCount: 1,
    distanceKm: 1000,
    firstFlightDate: "2025-01-01",
    firstFlightNumber: "NH1",
    isNew: false,
    airlines: [],
    registrations: [],
    ...o,
  };
}

describe("buildHierarchy", () => {
  it("按 制造商 → 系列 → 型号 汇总次数，按次数倒序", () => {
    const tree = buildHierarchy([
      stat({ icaoCode: "B789", flightCount: 3 }),
      stat({ icaoCode: "B788", model: "787-8", displayName: "Boeing 787-8", flightCount: 1 }),
      stat({ icaoCode: "B738", family: "737", model: "737-800", displayName: "Boeing 737-800", body: "narrow", flightCount: 5 }),
      stat({ icaoCode: "A359", manufacturer: "Airbus", family: "A350", model: "A350-900", displayName: "Airbus A350-900", flightCount: 2 }),
    ]);

    expect(tree.map((m) => [m.name, m.count])).toEqual([
      ["Boeing", 9],
      ["Airbus", 2],
    ]);
    expect(tree[0].families.map((f) => [f.name, f.count])).toEqual([
      ["737", 5],
      ["787", 4],
    ]);
    expect(tree[0].families[1].models.map((m) => [m.icaoCode, m.model, m.count])).toEqual([
      ["B789", "787-9", 3],
      ["B788", "787-8", 1],
    ]);
  });

  it("未识别机型放在最后一组", () => {
    const tree = buildHierarchy([
      stat({ icaoCode: "X9Z", manufacturer: null, family: null, model: null, displayName: null, body: null, recognized: false, flightCount: 9 }),
      stat({ flightCount: 1 }),
    ]);
    expect(tree.map((m) => m.name)).toEqual(["Boeing", "未识别机型"]);
    expect(tree[1].families[0].models[0]).toMatchObject({ icaoCode: "X9Z", model: "X9Z", count: 9 });
  });
});

describe("shareSegments", () => {
  it("计算百分比，去掉为 0 的部分", () => {
    expect(shareSegments({ narrow: 31, wide: 15, regional: 4, unknown: 0 })).toEqual([
      { key: "narrow", label: "窄体", count: 31, percent: 62 },
      { key: "wide", label: "宽体", count: 15, percent: 30 },
      { key: "regional", label: "支线", count: 4, percent: 8 },
    ]);
  });

  it("没有航班时为空", () => {
    expect(shareSegments({ narrow: 0, wide: 0, regional: 0, unknown: 0 })).toEqual([]);
  });
});
