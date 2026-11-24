import type { Metadata } from "next";
import { ComingSoon } from "@/components/ComingSoon";
import { PageHeader } from "@/components/PageHeader";

export const metadata: Metadata = { title: "录入航班" };

export default function Page() {
  return (
    <>
      <PageHeader title="录入航班" description="手动填写、航班号自动查询或 AI 解析" />
      <ComingSoon phase="P3" />
    </>
  );
}
