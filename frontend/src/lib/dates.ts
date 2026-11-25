/** 本地日期 YYYY-MM-DD（不是 UTC：日本早上 8 点时 UTC 还是前一天）。 */
export function todayLocal(now = new Date()) {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}
