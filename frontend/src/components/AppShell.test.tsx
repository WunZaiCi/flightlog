import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";

const pathname = vi.hoisted(() => ({ current: "/" }));
vi.mock("next/navigation", () => ({ usePathname: () => pathname.current }));
vi.mock("./auth/UserMenu", () => ({ UserMenu: () => null }));

describe("AppShell", () => {
  beforeEach(() => {
    pathname.current = "/";
  });

  it("桌面导航包含全部页面", () => {
    render(<AppShell>内容</AppShell>);
    const nav = screen.getByRole("navigation", { name: "主导航" });
    for (const label of ["总览", "航班", "录入", "机型", "地图", "设置"]) {
      expect(within(nav).getByRole("link", { name: label })).toBeInTheDocument();
    }
  });

  it("手机底部标签栏有 5 项，设置放在顶部栏", () => {
    render(<AppShell>内容</AppShell>);
    const tabBar = screen.getByRole("navigation", { name: "底部标签栏" });
    expect(within(tabBar).getAllByRole("link")).toHaveLength(5);
    expect(within(tabBar).queryByRole("link", { name: "设置" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "设置（手机）" })).toHaveAttribute("href", "/settings");
  });

  it("当前页面的导航项标记 aria-current", () => {
    pathname.current = "/flights/new";
    render(<AppShell>内容</AppShell>);
    const nav = screen.getByRole("navigation", { name: "主导航" });
    expect(within(nav).getByRole("link", { name: "录入" })).toHaveAttribute("aria-current", "page");
    expect(within(nav).getByRole("link", { name: "航班" })).not.toHaveAttribute("aria-current");
  });

  it("渲染页面内容", () => {
    render(<AppShell>页面内容</AppShell>);
    expect(screen.getByRole("main")).toHaveTextContent("页面内容");
  });
});
