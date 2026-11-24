"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./api";

export type RouteFlight = {
  id: number;
  flightDate: string;
  flightNumber: string;
  depIata: string;
  arrIata: string;
  airlineName: string | null;
  aircraft: string | null;
};

export type RouteStat = { from: string; to: string; count: number; distanceKm: number; flights: RouteFlight[] };

export type AirportStat = {
  iata: string;
  name: string;
  city: string | null;
  countryCode: string | null;
  lat: number;
  lon: number;
  visits: number;
  departures: number;
  arrivals: number;
};

export type RouteSummary = { from: string; to: string; distanceKm: number };

export type RoutesData = {
  routes: RouteStat[];
  airports: AirportStat[];
  totals: {
    flightCount: number;
    totalDistanceKm: number;
    countryCount: number;
    longest: RouteSummary | null;
    shortest: RouteSummary | null;
  };
};

export type MapFilters = { year?: string; type?: string; airline?: string };

export const routeKey = (r: { from: string; to: string }) => `${r.from}-${r.to}`;

export function useRoutes(filters: MapFilters) {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(filters)) if (v) params.set(k, v);
  const qs = params.toString();
  return useQuery({
    queryKey: ["stats", "routes", qs],
    queryFn: () => api.get<RoutesData>(qs ? `/api/stats/routes?${qs}` : "/api/stats/routes"),
    placeholderData: (previous) => previous,
  });
}
