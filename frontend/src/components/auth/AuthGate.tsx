"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { useMe } from "@/lib/auth";

/** 登录门禁：未登录跳到 /login?next=原地址；后端连不上时提示重试，不误跳登录页。 */
export function AuthGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams().toString();
  const me = useMe();

  const loggedOut = me.isSuccess && me.data === null;

  useEffect(() => {
    if (loggedOut) {
      const next = search ? `${pathname}?${search}` : pathname;
      router.replace(`/login?next=${encodeURIComponent(next)}`);
    }
  }, [loggedOut, pathname, search, router]);

  if (me.isError) {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-4 p-6 text-center">
        <p role="alert" className="text-navy">
          {me.error.message}
        </p>
        <Button onClick={() => me.refetch()}>重试</Button>
      </div>
    );
  }

  if (!me.data) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Spinner label="正在加载" />
      </div>
    );
  }

  return <>{children}</>;
}
