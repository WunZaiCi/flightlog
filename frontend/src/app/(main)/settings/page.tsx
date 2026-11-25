import type { Metadata } from "next";
import { AccountSection } from "@/components/auth/AccountSection";
import { ChangePasswordForm } from "@/components/auth/ChangePasswordForm";
import { PageHeader } from "@/components/PageHeader";
import { ApiStatus } from "@/components/settings/ApiStatus";

export const metadata: Metadata = { title: "设置" };

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="设置" description="账号、密码和 API key 状态" />
      <div className="space-y-6">
        <AccountSection />
        <ChangePasswordForm />
        <ApiStatus />
      </div>
    </>
  );
}
