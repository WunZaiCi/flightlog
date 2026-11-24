import type { Metadata } from "next";
import { Suspense } from "react";
import { PageHeader } from "@/components/PageHeader";
import { EditFlight } from "@/components/flights/EditFlight";

export const metadata: Metadata = { title: "编辑航班" };

export default function EditFlightPage() {
  return (
    <>
      <PageHeader title="编辑航班" />
      <Suspense>
        <EditFlight />
      </Suspense>
    </>
  );
}
