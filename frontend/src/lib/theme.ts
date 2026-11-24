/**
 * 在 JS 里使用的主题颜色（ECharts、MapLibre 只接受颜色值，不能用 Tailwind 类名）。
 * 值从 globals.css 的 @theme 变量读取，这里不写颜色值。
 */
export type ThemeColor =
  | "primary"
  | "accent"
  | "secondary"
  | "navy"
  | "muted"
  | "body-narrow"
  | "body-wide"
  | "body-regional"
  | "body-unknown"
  | "chart-grid"
  | "surface";

export function themeColor(name: ThemeColor): string {
  if (typeof window === "undefined") return "";
  return getComputedStyle(document.documentElement).getPropertyValue(`--color-${name}`).trim();
}
