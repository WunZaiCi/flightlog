export type LonLat = { lon: number; lat: number };

const toRad = (d: number) => (d * Math.PI) / 180;
const toDeg = (r: number) => (r * 180) / Math.PI;

/**
 * 两点之间大圆弧上的 segments + 1 个点（[经度, 纬度]），用于在地图上画航线。
 * 经度做了连续化处理：跨越 180° 经线时不会跳回 -180，MapLibre 会接着画到相邻的世界副本上，
 * 所以 NRT → SFO 画在太平洋上，而不是横穿亚欧大陆和大西洋。
 */
export function greatCirclePoints(a: LonLat, b: LonLat, segments = 64): [number, number][] {
  const lat1 = toRad(a.lat);
  const lon1 = toRad(a.lon);
  const lat2 = toRad(b.lat);
  const lon2 = toRad(b.lon);

  const d =
    2 *
    Math.asin(
      Math.sqrt(
        Math.sin((lat2 - lat1) / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin((lon2 - lon1) / 2) ** 2,
      ),
    );
  if (d === 0) return [
    [a.lon, a.lat],
    [b.lon, b.lat],
  ];

  const points: [number, number][] = [];
  for (let i = 0; i <= segments; i++) {
    const f = i / segments;
    const A = Math.sin((1 - f) * d) / Math.sin(d);
    const B = Math.sin(f * d) / Math.sin(d);
    const x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2);
    const y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2);
    const z = A * Math.sin(lat1) + B * Math.sin(lat2);
    points.push([toDeg(Math.atan2(y, x)), toDeg(Math.atan2(z, Math.sqrt(x * x + y * y)))]);
  }
  // 端点用原始坐标，避免浮点误差
  points[0] = [a.lon, a.lat];
  points[segments] = [b.lon, b.lat];

  // 经度连续化
  for (let i = 1; i < points.length; i++) {
    let lon = points[i][0];
    const prev = points[i - 1][0];
    while (lon - prev > 180) lon -= 360;
    while (lon - prev < -180) lon += 360;
    points[i] = [lon, points[i][1]];
  }
  return points;
}

/** 把 value 从 [min, max] 线性映射到 [from, to]；只有一种取值时返回 from。 */
export function scale(value: number, min: number, max: number, from: number, to: number) {
  if (max <= min) return from;
  return from + ((value - min) / (max - min)) * (to - from);
}

/**
 * 包含所有经度的最小经度范围 [west, east]：在圆周上找最大的空隙，从那里切开。
 * 跨太平洋的航线因此以太平洋为中心（east 可能大于 180），而不是把整个地球都框进去。
 */
export function lonSpan(lons: number[]): [number, number] {
  if (lons.length === 0) return [-180, 180];
  const sorted = [...lons].sort((a, b) => a - b);
  let gapAfter = sorted.length - 1; // 默认空隙在最大值和（最小值 + 360）之间，即不跨 180°
  let largestGap = sorted[0] + 360 - sorted[sorted.length - 1];
  for (let i = 0; i < sorted.length - 1; i++) {
    const gap = sorted[i + 1] - sorted[i];
    if (gap > largestGap) {
      largestGap = gap;
      gapAfter = i;
    }
  }
  if (gapAfter === sorted.length - 1) return [sorted[0], sorted[sorted.length - 1]];
  return [sorted[gapAfter + 1], sorted[gapAfter] + 360];
}
