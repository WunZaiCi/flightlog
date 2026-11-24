import type { Metadata } from "next";
import { ComingSoon } from "@/components/ComingSoon";
import { PageHeader } from "@/components/PageHeader";

export const metadata: Metadata = { title: "航班" };

export default function Page() {
  return (
    <>
      <PageHeader title="航班" description="按日期倒序，可筛选和搜索" />
      <ComingSoon phase="P3" />
    </>
  );
}
