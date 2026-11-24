import type { IconName } from "./Icon";

export type NavItem = {
  href: string;
  label: string;
  icon: IconName;
  /** 手机底部标签栏是否显示（设置收进顶部栏，避免标签过多） */
  inTabBar: boolean;
};

export const NAV_ITEMS: readonly NavItem[] = [
  { href: "/", label: "总览", icon: "home", inTabBar: true },
  { href: "/flights", label: "航班", icon: "list", inTabBar: true },
  { href: "/flights/new", label: "录入", icon: "plus", inTabBar: true },
  { href: "/aircraft", label: "机型", icon: "plane", inTabBar: true },
  { href: "/map", label: "地图", icon: "map", inTabBar: true },
  { href: "/settings", label: "设置", icon: "settings", inTabBar: false },
];

/** 当前路径对应哪个导航项：取最长前缀匹配（/flights/new 归「录入」而不是「航班」）。 */
export function activeNavHref(pathname: string): string | undefined {
  const path = pathname.replace(/\.html$/, "").replace(/\/+$/, "") || "/";
  let best: string | undefined;
  for (const { href } of NAV_ITEMS) {
    const matches = href === "/" ? path === "/" : path === href || path.startsWith(`${href}/`);
    if (matches && (best === undefined || href.length > best.length)) {
      best = href;
    }
  }
  return best;
}
