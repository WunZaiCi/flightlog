"use client";

import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { useFlights } from "@/lib/flights";
import { FlightList } from "./FlightList";

export function FlightsPage() {
  const flights = useFlights();
  if (flights.isPending) return <Spinner />;
  if (flights.isError) {
    return (
      <div role="alert" className="space-y-3 text-center">
        <p>{flights.error.message}</p>
        <Button onClick={() => flights.refetch()}>重试</Button>
      </div>
    );
  }
  return <FlightList flights={flights.data} />;
}
