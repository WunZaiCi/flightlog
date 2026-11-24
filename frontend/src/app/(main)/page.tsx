import type { Metadata } from "next";
import { ComingSoon } from "@/components/ComingSoon";
import { PageHeader } from "@/components/PageHeader";

export const metadata: Metadata = { title: { absolute: "FlightLog" } };

export default function Page() {
  return (
    <>
      <PageHeader title="总览" description="航班、机型、航司、机场和里程的统计" />
      <ComingSoon phase="P3" />
    </>
  );
}
