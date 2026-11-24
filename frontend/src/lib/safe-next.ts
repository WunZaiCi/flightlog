const AUTH_PAGES = ["/login", "/register"];

/** 登录后跳转的目标：只允许站内相对路径，防止被利用跳转到外部网站（开放重定向）。 */
export function safeNextPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) {
    return "/";
  }
  const pathname = next.split(/[?#]/)[0];
  if (AUTH_PAGES.includes(pathname)) return "/";
  return next;
}
