"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import type { Flight } from "./types";

/** 地球赤道周长（公里），用来把总里程换算成绕地球几圈。 */
export const EARTH_CIRCUMFERENCE_KM = 40_075;

export type UnlockedType = {
  icaoCode: string;
  displayName: string | null;
  recognized: boolean;
  firstFlightDate: string;
  firstFlightNumber: string;
};

export type Summary = {
  flightCount: number;
  aircraftTypeCount: number;
  unrecognizedTypeCount: number;
  airlineCount: number;
  airportCount: number;
  totalDistanceKm: number;
  recentFlights: Flight[];
  newlyUnlocked: UnlockedType[];
};

export function useSummary() {
  return useQuery({ queryKey: ["stats", "summary"], queryFn: () => api.get<Summary>("/api/stats/summary") });
}

export function earthLaps(km: number) {
  return km / EARTH_CIRCUMFERENCE_KM;
}

export function formatKm(km: number) {
  return `${Math.round(km).toLocaleString("en-US")} km`;
}
