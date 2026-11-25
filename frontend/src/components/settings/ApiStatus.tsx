"use client";

import { useAppConfig } from "@/lib/config";

/** 外部 API 的配置状态：只显示有没有配置，绝不显示 key 本身。 */
export function ApiStatus() {
  const config = useAppConfig();
  const rows = [
    { name: "航班号自动查询（AeroDataBox）", env: "AERODATABOX_API_KEY", enabled: config.data?.lookupEnabled },
    { name: "AI 解析（Gemini）", env: "GEMINI_API_KEY", enabled: config.data?.aiEnabled },
  ];
  return (
    <section aria-labelledby="api-status-title" className="rounded-xl bg-white p-4 ring-1 ring-slate-200">
      <h2 id="api-status-title" className="text-base font-semibold text-navy">
        API key 状态
      </h2>
      <ul className="mt-3 divide-y divide-slate-100 text-sm">
        {rows.map((r) => (
          <li key={r.env} className="flex flex-wrap items-center justify-between gap-2 py-2">
            <span>
              {r.name}
              <span className="ml-2 font-mono text-xs text-muted">{r.env}</span>
            </span>
            {r.enabled === undefined ? (
              <span className="text-muted">检查中…</span>
            ) : r.enabled ? (
              <span className="rounded bg-primary/10 px-2 py-0.5 text-primary">已配置</span>
            ) : (
              <span className="rounded bg-slate-100 px-2 py-0.5 text-muted">未配置（功能已禁用）</span>
            )}
          </li>
        ))}
      </ul>
      <p className="mt-2 text-xs text-muted">在项目根目录的 .env 里填写后重启应用生效。没有配置时手动录入照常可用。</p>
    </section>
  );
}
