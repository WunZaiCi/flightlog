import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/PageHeader";
import { FlightsPage } from "@/components/flights/FlightsPage";

export const metadata: Metadata = { title: "航班" };

export default function Page() {
  return (
    <>
      <PageHeader
        title="航班"
        description="按日期倒序，可筛选和搜索"
        actions={
          <Link
            href="/flights/new"
            className="hidden min-h-11 items-center rounded-lg bg-primary px-4 text-sm font-semibold text-white md:inline-flex"
          >
            录入航班
          </Link>
        }
      />
      <Suspense>
        <FlightsPage />
      </Suspense>
    </>
  );
}
