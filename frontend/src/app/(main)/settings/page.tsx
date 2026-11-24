import type { Metadata } from "next";
import { AccountSection } from "@/components/auth/AccountSection";
import { ComingSoon } from "@/components/ComingSoon";
import { PageHeader } from "@/components/PageHeader";

export const metadata: Metadata = { title: "设置" };

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="设置" description="账号、CSV 导入导出、备份说明和 API key 状态" />
      <div className="space-y-6">
        <AccountSection />
        <ComingSoon phase="P8" />
      </div>
    </>
  );
}
