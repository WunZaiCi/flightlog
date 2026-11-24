"use client";

import { useSearchParams } from "next/navigation";
import { useMemo } from "react";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { activeFilterCount, facets, parseFilters } from "@/lib/flight-filters";
import { useFlights } from "@/lib/flights";
import { FlightFilterBar } from "./FlightFilterBar";
import { FlightList } from "./FlightList";

export function FlightsPage() {
  const params = useSearchParams();
  const filters = useMemo(() => parseFilters(params), [params]);
  const all = useFlights();
  const filtered = useFlights(filters);
  const options = useMemo(() => facets(all.data ?? []), [all.data]);

  if (filtered.isPending) return <Spinner />;
  if (filtered.isError) {
    return (
      <div role="alert" className="space-y-3 text-center">
        <p>{filtered.error.message}</p>
        <Button onClick={() => filtered.refetch()}>重试</Button>
      </div>
    );
  }

  const hasFilters = activeFilterCount(filters) > 0;
  const showBar = hasFilters || (all.data?.length ?? 0) > 0;
  return (
    <>
      {showBar ? <FlightFilterBar filters={filters} options={options} /> : null}
      {hasFilters ? (
        <p className="mb-3 text-sm text-muted" aria-live="polite">
          找到 {filtered.data.length} 个航班
        </p>
      ) : null}
      {hasFilters && filtered.data.length === 0 ? (
        <p className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-muted">没有符合条件的航班</p>
      ) : (
        <FlightList flights={filtered.data} />
      )}
    </>
  );
}
