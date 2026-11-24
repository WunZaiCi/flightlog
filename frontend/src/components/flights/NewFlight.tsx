"use client";

import { useRouter } from "next/navigation";
import { FlightForm } from "./FlightForm";

export function NewFlight() {
  const router = useRouter();
  return (
    <div className="rounded-xl bg-white p-4 ring-1 ring-slate-200 md:p-6">
      <FlightForm onSaved={() => router.push("/flights")} onCancel={() => router.back()} />
    </div>
  );
}
