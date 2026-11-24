import type { Metadata } from "next";
import { PageHeader } from "@/components/PageHeader";
import { MapPage } from "@/components/map/MapPage";

export const metadata: Metadata = { title: "航线地图" };

export default function Page() {
  return (
    <>
      {/* 手机上地图全屏：标题只给读屏软件 */}
      <div className="max-md:sr-only">
        <PageHeader title="航线地图" description="飞过的航线和机场，点击查看详情" />
      </div>
      <MapPage />
    </>
  );
}
