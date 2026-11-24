/** 与后端 DTO 对应的类型。 */

export type Airport = {
  iata: string;
  icao: string | null;
  name: string;
  city: string | null;
  countryCode: string | null;
  lat: number;
  lon: number;
  tz?: string | null;
};

export type Airline = {
  iata: string | null;
  icao: string | null;
  name: string;
  countryCode: string | null;
};

export type BodyType = "narrow" | "wide" | "regional";

export type AircraftType = {
  icaoCode: string;
  manufacturer: string;
  family: string;
  model: string;
  displayName: string;
  body: BodyType;
};

export type FlightSource = "manual" | "api" | "ai" | "csv";

export type Flight = {
  id: number;
  flightDate: string;
  flightNumber: string;
  airlineIata: string | null;
  airlineName: string | null;
  dep: Omit<Airport, "icao" | "tz">;
  arr: Omit<Airport, "icao" | "tz">;
  aircraftTypeIcao: string | null;
  aircraftTypeRecognized: boolean;
  aircraftType: AircraftType | null;
  registration: string | null;
  distanceKm: number;
  notes: string | null;
  source: FlightSource;
  createdAt: string;
  updatedAt: string;
};

export type FlightInput = {
  flightDate: string;
  flightNumber: string;
  depIata: string;
  arrIata: string;
  aircraftType?: string | null;
  registration?: string | null;
  notes?: string | null;
  source?: FlightSource;
  allowDuplicate?: boolean;
};
