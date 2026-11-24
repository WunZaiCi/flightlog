"use client";

import "maplibre-gl/dist/maplibre-gl.css";
import {
  LngLatBounds,
  Map as MapLibreMap,
  NavigationControl,
  setWorkerUrl,
  type GeoJSONSource,
  type MapLayerMouseEvent,
  type StyleSpecification,
} from "maplibre-gl";
import { useEffect, useRef, useState } from "react";
import { greatCirclePoints, lonSpan, scale } from "@/lib/geo";
import { routeKey, type AirportStat, type RouteStat } from "@/lib/routes";
import { themeColor } from "@/lib/theme";

// worker 由 scripts/copy-maplibre-worker.mjs 复制到 public/maplibre/
setWorkerUrl("/maplibre/maplibre-gl-worker.mjs");

/** 联网时的底图：OpenFreeMap positron（免费，不需要 key，样式自带 OpenStreetMap 署名）。 */
export const ONLINE_STYLE = "https://tiles.openfreemap.org/styles/positron";
/** 在线样式超过这么久还没加载好，就改用离线底图。 */
const STYLE_TIMEOUT_MS = 8000;

/** 离线底图：打包进项目的 Natural Earth 陆地轮廓。 */
function offlineStyle(): StyleSpecification {
  return {
    version: 8,
    sources: {
      land: {
        type: "geojson",
        data: "/basemap/ne_110m_land.geojson",
        attribution: '<a href="https://www.naturalearthdata.com/" target="_blank" rel="noopener">Natural Earth</a>',
      },
    },
    layers: [
      { id: "water", type: "background", paint: { "background-color": themeColor("map-water") } },
      { id: "land", type: "fill", source: "land", paint: { "fill-color": themeColor("map-land") } },
      { id: "coast", type: "line", source: "land", paint: { "line-color": themeColor("map-coast"), "line-width": 0.5 } },
    ],
  };
}

type RouteMapProps = {
  routes: RouteStat[];
  airports: AirportStat[];
  selectedRoute: string | null;
  selectedAirport: string | null;
  onSelectRoute: (key: string) => void;
  onSelectAirport: (iata: string) => void;
};

/**
 * 航线地图：大圆弧线（粗细表示飞过的次数）、机场点（大小表示到访次数），点击查看详情。
 * 在线底图加载失败或超时时自动改用离线底图，航线照常显示。
 */
export function RouteMap(props: RouteMapProps) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<MapLibreMap | null>(null);
  const latest = useRef(props);
  useEffect(() => {
    latest.current = props;
  });
  const [offline, setOffline] = useState(false);

  // 创建地图（只一次）
  useEffect(() => {
    if (!container.current) return;
    const instance = new MapLibreMap({
      container: container.current,
      style: ONLINE_STYLE,
      center: [135, 35],
      zoom: 2,
      attributionControl: { compact: true },
      dragRotate: false,
      pitchWithRotate: false,
    });
    instance.touchZoomRotate.disableRotation();
    instance.addControl(new NavigationControl({ showCompass: false }), "top-left");
    map.current = instance;

    let switched = false;
    const switchToOffline = () => {
      if (switched) return;
      switched = true;
      setOffline(true);
      instance.setStyle(offlineStyle());
    };
    const timer = setTimeout(() => {
      if (!instance.isStyleLoaded()) switchToOffline();
    }, STYLE_TIMEOUT_MS);
    instance.on("error", () => {
      // 样式还没加载好时出错（例如断网），就改用离线底图；之后个别瓦片加载失败不处理
      if (!instance.isStyleLoaded()) switchToOffline();
    });

    // 每次（重新）加载样式后都要重新加上航线和机场图层
    instance.on("style.load", () => {
      addOverlay(instance);
      updateData(instance, latest.current, true);
    });

    // 记录已经由 worker 解析好的航线数量（e2e 用来确认航线真的画出来了）
    instance.on("sourcedata", (e) => {
      if (e.sourceId !== "routes" || !e.isSourceLoaded) return;
      const keys = new Set(instance.querySourceFeatures("routes").map((f) => f.properties?.key));
      container.current?.setAttribute("data-features", String(keys.size));
    });

    for (const layer of ["routes-hit", "airports"]) {
      instance.on("mouseenter", layer, () => (instance.getCanvas().style.cursor = "pointer"));
      instance.on("mouseleave", layer, () => (instance.getCanvas().style.cursor = ""));
    }
    instance.on("click", "airports", (e: MapLayerMouseEvent) => {
      const iata = e.features?.[0]?.properties?.iata;
      if (iata) latest.current.onSelectAirport(String(iata));
    });
    instance.on("click", "routes-hit", (e: MapLayerMouseEvent) => {
      // 点到机场时以机场为准
      if (instance.queryRenderedFeatures(e.point, { layers: ["airports"] }).length > 0) return;
      const key = e.features?.[0]?.properties?.key;
      if (key) latest.current.onSelectRoute(String(key));
    });

    return () => {
      clearTimeout(timer);
      instance.remove();
      map.current = null;
    };
  }, []);

  // 数据或选中项变化时更新图层
  const { routes, airports, selectedRoute, selectedAirport } = props;
  useEffect(() => {
    if (map.current?.isStyleLoaded()) updateData(map.current, latest.current, false);
  }, [routes, airports, selectedRoute, selectedAirport]);

  // 数据变化时把视野调整到所有机场
  useEffect(() => {
    if (map.current) fitToAirports(map.current, airports);
  }, [airports]);

  return (
    <div className="relative h-full w-full">
      <div ref={container} className="h-full w-full" data-testid="route-map" data-basemap={offline ? "offline" : "online"} />
      {offline ? (
        <p className="pointer-events-none absolute bottom-8 left-2 rounded bg-white/90 px-2 py-1 text-xs text-muted shadow-sm">
          离线底图
        </p>
      ) : null}
    </div>
  );
}

function addOverlay(m: MapLibreMap) {
  if (m.getSource("routes")) return;
  const primary = themeColor("primary");
  const navy = themeColor("navy");
  const accent = themeColor("accent");
  const surface = themeColor("surface");
  const empty = { type: "FeatureCollection" as const, features: [] };

  m.addSource("routes", { type: "geojson", data: empty });
  m.addSource("airports", { type: "geojson", data: empty });

  m.addLayer({
    id: "routes",
    type: "line",
    source: "routes",
    layout: { "line-cap": "round", "line-join": "round" },
    paint: {
      "line-color": ["case", ["get", "selected"], accent, primary],
      "line-width": ["get", "width"],
      "line-opacity": ["case", ["get", "selected"], 1, 0.75],
    },
  });
  // 更宽的透明线，方便手指点中细航线
  m.addLayer({
    id: "routes-hit",
    type: "line",
    source: "routes",
    paint: { "line-color": primary, "line-width": 16, "line-opacity": 0 },
  });
  m.addLayer({
    id: "airports",
    type: "circle",
    source: "airports",
    paint: {
      "circle-radius": ["get", "radius"],
      "circle-color": ["case", ["get", "selected"], accent, navy],
      "circle-stroke-color": surface,
      "circle-stroke-width": 2,
    },
  });
}

function updateData(m: MapLibreMap, props: RouteMapProps, fit: boolean) {
  const routesSource = m.getSource<GeoJSONSource>("routes");
  const airportsSource = m.getSource<GeoJSONSource>("airports");
  if (!routesSource || !airportsSource) return;

  const byIata = new Map(props.airports.map((a) => [a.iata, a]));
  const counts = props.routes.map((r) => r.count);
  const minCount = Math.min(...counts, 1);
  const maxCount = Math.max(...counts, 1);
  const visits = props.airports.map((a) => a.visits);
  const minVisits = Math.min(...visits, 1);
  const maxVisits = Math.max(...visits, 1);

  routesSource.setData({
    type: "FeatureCollection",
    features: props.routes.flatMap((r) => {
      const a = byIata.get(r.from);
      const b = byIata.get(r.to);
      if (!a || !b) return [];
      const key = routeKey(r);
      return [
        {
          type: "Feature" as const,
          properties: { key, count: r.count, width: scale(r.count, minCount, maxCount, 1.5, 6), selected: key === props.selectedRoute },
          geometry: { type: "LineString" as const, coordinates: greatCirclePoints(a, b, 64) },
        },
      ];
    }),
  });
  airportsSource.setData({
    type: "FeatureCollection",
    features: props.airports.map((a) => ({
      type: "Feature" as const,
      properties: { iata: a.iata, radius: scale(a.visits, minVisits, maxVisits, 4, 10), selected: a.iata === props.selectedAirport },
      geometry: { type: "Point" as const, coordinates: [a.lon, a.lat] },
    })),
  });
  if (fit) fitToAirports(m, props.airports);
}

function fitToAirports(m: MapLibreMap, airports: AirportStat[]) {
  if (airports.length === 0) return;
  if (airports.length === 1) {
    m.easeTo({ center: [airports[0].lon, airports[0].lat], zoom: 5, duration: 0 });
    return;
  }
  // 经度取最小覆盖范围（跨太平洋时以太平洋为中心），纬度取最小到最大
  const [west, east] = lonSpan(airports.map((a) => a.lon));
  const lats = airports.map((a) => a.lat);
  const bounds = new LngLatBounds([west, Math.min(...lats)], [east, Math.max(...lats)]);
  m.fitBounds(bounds, { padding: 48, maxZoom: 6, duration: 0 });
}
