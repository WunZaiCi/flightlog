"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { UserMenu } from "./auth/UserMenu";
import { Icon } from "./Icon";
import { NAV_ITEMS, activeNavHref } from "./nav-items";

/**
 * 应用外壳。
 * - 桌面（md 及以上）：顶部深海军蓝导航栏，包含全部页面。
 * - 手机：顶部只有标题和「设置」，主要页面放在底部标签栏（点击区域不小于 44px）。
 */
export function AppShell({ children }: { children: ReactNode }) {
  const active = activeNavHref(usePathname());
  const tabItems = NAV_ITEMS.filter((item) => item.inTabBar);

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-20 bg-navy pt-[env(safe-area-inset-top)] text-white shadow-sm">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
          <Link href="/" className="flex min-h-11 items-center gap-2 text-lg font-semibold tracking-tight">
            <Icon name="plane" className="size-5 text-accent" />
            FlightLog
          </Link>

          <nav aria-label="主导航" className="hidden flex-1 md:block">
            <ul className="flex items-center gap-1">
              {NAV_ITEMS.map((item) => {
                const isActive = item.href === active;
                return (
                  <li key={item.href} className={item.href === "/settings" ? "ml-auto" : undefined}>
                    <Link
                      href={item.href}
                      aria-current={isActive ? "page" : undefined}
                      className={`flex min-h-11 items-center rounded-md px-3 text-sm font-medium transition-colors ${
                        isActive ? "bg-white/15 text-white" : "text-white/75 hover:bg-white/10 hover:text-white"
                      }`}
                    >
                      {item.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>

          <div className="hidden md:block">
            <UserMenu />
          </div>

          <Link
            href="/settings"
            aria-label="设置（手机）"
            aria-current={active === "/settings" ? "page" : undefined}
            className="ml-auto flex size-11 items-center justify-center rounded-md text-white/85 hover:bg-white/10 md:hidden"
          >
            <Icon name="settings" />
          </Link>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pt-6 pb-[calc(5rem+env(safe-area-inset-bottom))] md:pb-10">
        {children}
      </main>

      <nav
        aria-label="底部标签栏"
        className="fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden"
      >
        <ul className="grid grid-cols-5">
          {tabItems.map((item) => {
            const isActive = item.href === active;
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  aria-current={isActive ? "page" : undefined}
                  className={`flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs font-medium ${
                    isActive ? "text-primary" : "text-muted"
                  }`}
                >
                  <Icon name={item.icon} className="size-6" />
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
    </div>
  );
}
