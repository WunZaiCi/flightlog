import type { Flight } from "./types";

/** 航班列表的筛选条件，对应 GET /api/flights 的参数，也保存在页面 URL 里。 */
export type FlightFilters = {
  year?: string;
  airline?: string;
  type?: string;
  airport?: string;
  q?: string;
};

const KEYS = ["year", "airline", "type", "airport", "q"] as const;

export function parseFilters(params: URLSearchParams): FlightFilters {
  const filters: FlightFilters = {};
  for (const key of KEYS) {
    const value = params.get(key)?.trim();
    if (value) filters[key] = value;
  }
  return filters;
}

export function toQueryString(filters: FlightFilters): string {
  const params = new URLSearchParams();
  for (const key of KEYS) {
    const value = filters[key]?.trim();
    if (value) params.set(key, value);
  }
  return params.toString();
}

export function activeFilterCount(filters: FlightFilters): number {
  return KEYS.filter((key) => filters[key]?.trim()).length;
}

export type FacetOption = { value: string; label: string; count: number };

export type Facets = {
  years: string[];
  airlines: FacetOption[];
  types: FacetOption[];
  airports: FacetOption[];
};

/** 从用户的全部航班里得出筛选下拉框的选项（只列出坐过的）。 */
export function facets(flights: Flight[]): Facets {
  const years = new Set<string>();
  const airlines = new Map<string, FacetOption>();
  const types = new Map<string, FacetOption>();
  const airports = new Map<string, FacetOption>();

  const bump = (map: Map<string, FacetOption>, value: string, label: string) => {
    const existing = map.get(value);
    if (existing) existing.count++;
    else map.set(value, { value, label, count: 1 });
  };

  for (const f of flights) {
    years.add(f.flightDate.slice(0, 4));
    if (f.airlineIata) bump(airlines, f.airlineIata, f.airlineName ? `${f.airlineIata} · ${f.airlineName}` : f.airlineIata);
    if (f.aircraftTypeIcao) bump(types, f.aircraftTypeIcao, f.aircraftType?.displayName ?? `${f.aircraftTypeIcao}（未识别）`);
    for (const a of [f.dep, f.arr]) bump(airports, a.iata, a.city ? `${a.iata} · ${a.city}` : a.iata);
  }

  const byCount = (a: FacetOption, b: FacetOption) => b.count - a.count || a.value.localeCompare(b.value);
  return {
    years: [...years].sort().reverse(),
    airlines: [...airlines.values()].sort(byCount),
    types: [...types.values()].sort(byCount),
    airports: [...airports.values()].sort(byCount),
  };
}
