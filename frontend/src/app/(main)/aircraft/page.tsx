import type { Metadata } from "next";
import { ComingSoon } from "@/components/ComingSoon";
import { PageHeader } from "@/components/PageHeader";

export const metadata: Metadata = { title: "机型统计" };

export default function Page() {
  return (
    <>
      <PageHeader title="机型统计" description="坐过的机型、排行榜和收集墙" />
      <ComingSoon phase="P4" />
    </>
  );
}
