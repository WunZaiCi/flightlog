"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import type { Flight, FlightInput } from "./types";

export const FLIGHTS_KEY = ["flights"] as const;

export function useFlights() {
  return useQuery({ queryKey: [...FLIGHTS_KEY, "list"], queryFn: () => api.get<Flight[]>("/api/flights") });
}

export function useFlight(id: number | null) {
  return useQuery({
    queryKey: [...FLIGHTS_KEY, "detail", id],
    queryFn: () => api.get<Flight>(`/api/flights/${id}`),
    enabled: id !== null,
  });
}

/** 航班变动后，列表、统计等依赖航班的数据都要重新取。 */
function useInvalidateFlightData() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: FLIGHTS_KEY }),
      queryClient.invalidateQueries({ queryKey: ["stats"] }),
    ]);
}

export function useCreateFlight() {
  const invalidate = useInvalidateFlightData();
  return useMutation({
    mutationFn: (input: FlightInput) => api.post<Flight>("/api/flights", input),
    onSuccess: invalidate,
  });
}

export function useUpdateFlight(id: number) {
  const invalidate = useInvalidateFlightData();
  return useMutation({
    mutationFn: (input: FlightInput) => api.put<Flight>(`/api/flights/${id}`, input),
    onSuccess: invalidate,
  });
}

export function useDeleteFlight() {
  const invalidate = useInvalidateFlightData();
  return useMutation({
    mutationFn: (id: number) => api.delete<void>(`/api/flights/${id}`),
    onSuccess: invalidate,
  });
}
