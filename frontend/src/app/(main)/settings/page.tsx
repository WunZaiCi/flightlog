import type { Metadata } from "next";
import { ComingSoon } from "@/components/ComingSoon";
import { PageHeader } from "@/components/PageHeader";

export const metadata: Metadata = { title: "设置" };

export default function Page() {
  return (
    <>
      <PageHeader title="设置" description="CSV 导入导出、备份说明和 API key 状态" />
      <ComingSoon phase="P8" />
    </>
  );
}
