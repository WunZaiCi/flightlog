import type { Metadata } from "next";
import { ComingSoon } from "@/components/ComingSoon";
import { PageHeader } from "@/components/PageHeader";

export const metadata: Metadata = { title: "航线地图" };

export default function Page() {
  return (
    <>
      <PageHeader title="航线地图" description="飞过的航线和机场" />
      <ComingSoon phase="P5" />
    </>
  );
}
