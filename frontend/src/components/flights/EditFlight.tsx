"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Spinner } from "@/components/ui/Spinner";
import { useFlight, useUpdateFlight } from "@/lib/flights";
import { FlightForm } from "./FlightForm";

/** 静态导出不支持动态路由，所以编辑页用 /flights/edit?id=123。 */
export function EditFlight() {
  const router = useRouter();
  const raw = useSearchParams().get("id");
  const id = raw && /^\d+$/.test(raw) ? Number(raw) : null;
  const flight = useFlight(id);
  const update = useUpdateFlight(id ?? 0);

  if (id === null || flight.isError) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center">
        <p role="alert" className="text-muted">
          {flight.error?.message ?? "航班不存在"}
        </p>
        <Link href="/flights" className="mt-4 inline-flex min-h-11 items-center text-primary">
          返回航班列表
        </Link>
      </div>
    );
  }
  if (!flight.data) return <Spinner />;

  const f = flight.data;
  return (
    <div className="rounded-xl bg-white p-4 ring-1 ring-slate-200 md:p-6">
      <FlightForm
        key={f.id}
        source={f.source}
        initial={{
          flightDate: f.flightDate,
          flightNumber: f.flightNumber,
          depIata: f.dep.iata,
          arrIata: f.arr.iata,
          aircraftType: f.aircraftTypeIcao,
          registration: f.registration,
          notes: f.notes,
        }}
        submit={update.mutateAsync}
        onSaved={() => router.push("/flights")}
        onCancel={() => router.back()}
      />
    </div>
  );
}
