import { api } from "./api";
import type { AircraftType, Airline, Airport } from "./types";

const q = (query: string) => encodeURIComponent(query);

export const searchAirports = (query: string) => api.get<Airport[]>(`/api/ref/airports?q=${q(query)}`);
export const searchAirlines = (query: string) => api.get<Airline[]>(`/api/ref/airlines?q=${q(query)}`);
export const searchAircraftTypes = (query: string) =>
  api.get<AircraftType[]>(`/api/ref/aircraft-types?q=${q(query)}`);

export function airportLabel(a: Pick<Airport, "name" | "city" | "countryCode">) {
  return [a.name, [a.city, a.countryCode].filter(Boolean).join(", ")].filter(Boolean).join(" · ");
}
