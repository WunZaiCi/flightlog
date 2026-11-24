"use client";

import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { useLogout } from "@/lib/auth";

export function LogoutButton({ className, variant = "ghost" }: { className?: string; variant?: "ghost" | "danger" }) {
  const router = useRouter();
  const logout = useLogout();
  return (
    <Button
      variant={variant}
      className={className}
      loading={logout.isPending}
      onClick={() => logout.mutate(undefined, { onSettled: () => router.replace("/login") })}
    >
      退出登录
    </Button>
  );
}
