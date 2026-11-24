import { describe, expect, it } from "vitest";
import { greatCirclePoints, lonSpan, scale } from "./geo";

const HND = { lon: 139.78, lat: 35.55 };
const FUK = { lon: 130.45, lat: 33.58 };
const SFO = { lon: -122.38, lat: 37.62 };

describe("greatCirclePoints", () => {
  it("起点和终点就是两个机场", () => {
    const pts = greatCirclePoints(HND, FUK, 32);
    expect(pts).toHaveLength(33);
    expect(pts[0][0]).toBeCloseTo(139.78, 5);
    expect(pts[0][1]).toBeCloseTo(35.55, 5);
    expect(pts[32][0]).toBeCloseTo(130.45, 5);
    expect(pts[32][1]).toBeCloseTo(33.58, 5);
  });

  it("大圆弧向极地方向凸起（北半球的长航线中点纬度更高）", () => {
    const pts = greatCirclePoints(HND, SFO, 64);
    const midLat = pts[32][1];
    expect(midLat).toBeGreaterThan(Math.max(HND.lat, SFO.lat) + 5);
  });

  it("跨越 180° 经线时经度连续，不会绕地球另一边", () => {
    const pts = greatCirclePoints(HND, SFO, 64);
    for (let i = 1; i < pts.length; i++) {
      expect(Math.abs(pts[i][0] - pts[i - 1][0])).toBeLessThan(10);
    }
    // 往东飞越日期变更线：终点经度表示为 -122.38 + 360
    expect(pts[64][0]).toBeCloseTo(237.62, 5);
  });

  it("同一个点返回两个相同的点", () => {
    expect(greatCirclePoints(HND, HND, 8)).toEqual([
      [139.78, 35.55],
      [139.78, 35.55],
    ]);
  });
});

describe("scale", () => {
  it("把数值线性映射到范围内，最大值对应上限", () => {
    expect(scale(1, 1, 10, 2, 8)).toBe(2);
    expect(scale(10, 1, 10, 2, 8)).toBe(8);
    expect(scale(5.5, 1, 10, 2, 8)).toBe(5);
  });

  it("只有一种取值时用下限", () => {
    expect(scale(3, 3, 3, 2, 8)).toBe(2);
  });
});

describe("lonSpan", () => {
  it("普通情况：最小到最大经度", () => {
    expect(lonSpan([130.45, 139.78, 135.24])).toEqual([130.45, 139.78]);
  });

  it("跨太平洋时从最大空隙处切开，东边界可以超过 180", () => {
    // 东京、旧金山、新加坡：最大空隙在大西洋一侧，范围应是 103.99 → 237.62（太平洋居中）
    const [west, east] = lonSpan([139.78, -122.38, 103.99]);
    expect(west).toBeCloseTo(103.99, 5);
    expect(east).toBeCloseTo(237.62, 5);
  });

  it("欧洲到日本：不跨日期变更线，保持原样", () => {
    expect(lonSpan([-0.45, 139.78])).toEqual([-0.45, 139.78]);
  });

  it("只有一个点", () => {
    expect(lonSpan([10])).toEqual([10, 10]);
  });
});
