import type { Metadata } from "next";
import { PageHeader } from "@/components/PageHeader";
import { Overview } from "@/components/home/Overview";

export const metadata: Metadata = { title: { absolute: "FlightLog" } };

export default function HomePage() {
  return (
    <>
      <PageHeader title="总览" description="航班、机型、航司、机场和里程" />
      <Overview />
    </>
  );
}
