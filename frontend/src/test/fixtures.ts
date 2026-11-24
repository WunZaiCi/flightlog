import type { Flight } from "@/lib/types";

/** 测试用的航班数据，默认是 NH242 FUK→HND B789。 */
export function flight(overrides: Partial<Flight> = {}): Flight {
  return {
    id: 1,
    flightDate: "2026-11-03",
    flightNumber: "NH242",
    airlineIata: "NH",
    airlineName: "All Nippon Airways",
    dep: { iata: "FUK", name: "Fukuoka Airport", city: "Fukuoka", countryCode: "JP", lat: 33.58, lon: 130.45 },
    arr: { iata: "HND", name: "Tokyo Haneda International Airport", city: "Tokyo", countryCode: "JP", lat: 35.55, lon: 139.78 },
    aircraftTypeIcao: "B789",
    aircraftTypeRecognized: true,
    aircraftType: { icaoCode: "B789", manufacturer: "Boeing", family: "787", model: "787-9", displayName: "Boeing 787-9", body: "wide" },
    registration: "JA891A",
    distanceKm: 880,
    notes: null,
    source: "manual",
    createdAt: "2026-11-03T00:00:00Z",
    updatedAt: "2026-11-03T00:00:00Z",
    ...overrides,
  };
}
