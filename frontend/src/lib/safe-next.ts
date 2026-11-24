const AUTH_PAGES = ["/login", "/register"];
const PLACEHOLDER_ORIGIN = "http://flightlog.invalid";

/**
 * 登录后跳转的目标：只允许站内相对路径，防止被利用跳转到外部网站（开放重定向）。
 *
 * 先拒绝控制字符和反斜杠（URL 解析会去掉 \t \n \r，"/\t/evil.com" 实际是 "//evil.com"），
 * 再按 URL 规范解析，解析结果必须仍然是同源地址。
 */
export function safeNextPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || /[\u0000-\u001F\u007F\\]/.test(next)) {
    return "/";
  }

  let url: URL;
  try {
    url = new URL(next, PLACEHOLDER_ORIGIN);
  } catch {
    return "/";
  }
  if (url.origin !== PLACEHOLDER_ORIGIN || AUTH_PAGES.includes(url.pathname)) {
    return "/";
  }
  return url.pathname + url.search + url.hash;
}
