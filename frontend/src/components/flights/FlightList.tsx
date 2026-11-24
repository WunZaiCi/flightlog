"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { useDeleteFlight } from "@/lib/flights";
import type { Flight } from "@/lib/types";

export function FlightList({ flights, emptyText = "还没有航班记录" }: { flights: Flight[]; emptyText?: string }) {
  if (flights.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center">
        <p className="text-muted">{emptyText}</p>
        <Link
          href="/flights/new"
          className="mt-4 inline-flex min-h-11 items-center rounded-lg bg-primary px-4 text-sm font-semibold text-white"
        >
          录入第一个航班
        </Link>
      </div>
    );
  }
  return (
    <ul className="space-y-3">
      {flights.map((f) => (
        <FlightCard key={f.id} flight={f} />
      ))}
    </ul>
  );
}

function FlightCard({ flight }: { flight: Flight }) {
  const remove = useDeleteFlight();
  const [confirming, setConfirming] = useState(false);

  return (
    <li className="rounded-xl bg-white p-4 ring-1 ring-slate-200">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <div className="flex items-baseline gap-2">
          <span className="font-mono text-lg font-semibold text-navy">{flight.flightNumber}</span>
          {flight.airlineName ? <span className="text-sm text-muted">{flight.airlineName}</span> : null}
        </div>
        <time dateTime={flight.flightDate} className="text-sm text-muted">
          {flight.flightDate}
        </time>
      </div>

      <div className="mt-2 flex items-center gap-2 text-navy">
        <AirportCode code={flight.dep.iata} city={flight.dep.city} />
        <span aria-hidden="true" className="flex-1 border-t border-dashed border-slate-300" />
        <span className="text-xs text-muted">{flight.distanceKm.toLocaleString("zh-CN")} km</span>
        <span aria-hidden="true" className="flex-1 border-t border-dashed border-slate-300" />
        <AirportCode code={flight.arr.iata} city={flight.arr.city} />
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
        {flight.aircraftType ? (
          <span>{flight.aircraftType.displayName}</span>
        ) : flight.aircraftTypeIcao ? (
          <span className="rounded bg-accent/15 px-1.5 py-0.5 text-xs text-navy">未识别机型 {flight.aircraftTypeIcao}</span>
        ) : (
          <span className="text-muted">机型未填写</span>
        )}
        {flight.registration ? <span className="font-mono text-muted">{flight.registration}</span> : null}
      </div>
      {flight.notes ? <p className="mt-2 text-sm text-muted">{flight.notes}</p> : null}

      <div className="mt-3 flex flex-wrap justify-end gap-2">
        {confirming ? (
          <>
            <span className="self-center text-sm text-muted">删除后不能恢复</span>
            <Button variant="ghost" onClick={() => setConfirming(false)}>
              取消
            </Button>
            <Button variant="danger" loading={remove.isPending} onClick={() => remove.mutate(flight.id)}>
              确认删除
            </Button>
          </>
        ) : (
          <>
            <Link
              href={`/flights/edit?id=${flight.id}`}
              aria-label={`编辑 ${flight.flightNumber}`}
              className="inline-flex min-h-11 items-center rounded-lg px-3 text-sm font-medium text-primary hover:bg-primary/10"
            >
              编辑
            </Link>
            <Button variant="ghost" aria-label={`删除 ${flight.flightNumber}`} onClick={() => setConfirming(true)}>
              删除
            </Button>
          </>
        )}
      </div>
      {remove.error ? (
        <p role="alert" className="mt-2 text-sm text-red-600">
          {remove.error.message}
        </p>
      ) : null}
    </li>
  );
}

function AirportCode({ code, city }: { code: string; city: string | null }) {
  return (
    <span className="flex flex-col items-center leading-tight">
      <span className="font-mono text-xl font-bold">{code}</span>
      {city ? <span className="max-w-24 truncate text-xs text-muted">{city}</span> : null}
    </span>
  );
}
