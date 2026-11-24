"use client";

import { useMe } from "@/lib/auth";
import { LogoutButton } from "./LogoutButton";

/** 桌面顶部栏右侧：当前用户和退出按钮（手机上在「设置」页里）。 */
export function UserMenu() {
  const { data: user } = useMe();
  if (!user) return null;
  return (
    <div className="flex items-center gap-1">
      <span className="max-w-40 truncate text-sm text-white/80" title={user.email}>
        {user.displayName}
      </span>
      <LogoutButton className="!text-white/85 hover:!bg-white/10" />
    </div>
  );
}
