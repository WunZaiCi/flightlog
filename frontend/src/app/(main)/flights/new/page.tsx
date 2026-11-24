import type { Metadata } from "next";
import { PageHeader } from "@/components/PageHeader";
import { NewFlight } from "@/components/flights/NewFlight";

export const metadata: Metadata = { title: "录入航班" };

export default function NewFlightPage() {
  return (
    <>
      <PageHeader title="录入航班" description="手动填写；航班号自动查询和 AI 解析在后续阶段加入" />
      <NewFlight />
    </>
  );
}
