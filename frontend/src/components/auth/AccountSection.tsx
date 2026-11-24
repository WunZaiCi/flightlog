"use client";

import { useMe } from "@/lib/auth";
import { LogoutButton } from "./LogoutButton";

export function AccountSection() {
  const { data: user } = useMe();
  if (!user) return null;
  return (
    <section className="rounded-xl bg-white p-4 ring-1 ring-slate-200">
      <h2 className="text-base font-semibold text-navy">账号</h2>
      <dl className="mt-3 space-y-1 text-sm">
        <div className="flex gap-2">
          <dt className="w-12 shrink-0 text-muted">昵称</dt>
          <dd className="truncate">{user.displayName}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="w-12 shrink-0 text-muted">邮箱</dt>
          <dd className="truncate">{user.email}</dd>
        </div>
      </dl>
      <LogoutButton variant="danger" className="mt-4 w-full sm:w-auto" />
    </section>
  );
}
