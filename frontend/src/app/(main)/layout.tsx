import { Suspense } from "react";
import { AppShell } from "@/components/AppShell";
import { AuthGate } from "@/components/auth/AuthGate";

export default function MainLayout({ children }: LayoutProps<"/">) {
  return (
    <Suspense>
      <AuthGate>
        <AppShell>{children}</AppShell>
      </AuthGate>
    </Suspense>
  );
}
