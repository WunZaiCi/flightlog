import type { Metadata } from "next";
import { PageHeader } from "@/components/PageHeader";
import { NewFlight } from "@/components/flights/NewFlight";

export const metadata: Metadata = { title: "录入航班" };

export default function NewFlightPage() {
  return (
    <>
      <PageHeader title="录入航班" description="手动填写、航班号自动查询或 AI 解析，都需要确认后才保存" />
      <NewFlight />
    </>
  );
}
