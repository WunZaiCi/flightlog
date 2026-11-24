"use client";

import Link from "next/link";
import { useId, useMemo, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { NewBadge } from "@/components/ui/NewBadge";
import { Spinner } from "@/components/ui/Spinner";
import { buildHierarchy, typeName, useAircraftStats, type AircraftTypeStat } from "@/lib/aircraft-stats";
import { BodyShareBar } from "./BodyShareBar";
import { RankingChart } from "./RankingChart";
import { TypeDetail } from "./TypeDetail";
import { YearTrendChart } from "./YearTrendChart";

/** 机型统计页：占比、排行榜、按年份变化、分层统计、收集墙，点机型看详情。 */
export function AircraftPage() {
  const stats = useAircraftStats();
  const [selected, setSelected] = useState<AircraftTypeStat | null>(null);
  const hierarchy = useMemo(() => buildHierarchy(stats.data?.types ?? []), [stats.data]);

  if (stats.isPending) return <Spinner />;
  if (stats.isError) {
    return (
      <div role="alert" className="space-y-3 text-center">
        <p>{stats.error.message}</p>
        <Button onClick={() => stats.refetch()}>重试</Button>
      </div>
    );
  }

  const { types, bodyShare, byYear } = stats.data;
  if (types.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center">
        <p className="text-navy">还没有填写机型的航班</p>
        <p className="mt-1 text-sm text-muted">录入航班时填上机型，这里就会出现统计和收集墙。</p>
        <Link href="/flights/new" className="mt-4 inline-flex min-h-11 items-center rounded-lg bg-primary px-4 text-sm font-semibold text-white">
          录入航班
        </Link>
      </div>
    );
  }

  const unrecognized = types.filter((t) => !t.recognized);

  return (
    <div className="space-y-8">
      {unrecognized.length > 0 ? (
        <p role="note" className="rounded-lg bg-accent/15 px-3 py-2 text-sm text-navy ring-1 ring-accent/40">
          有 {unrecognized.length} 个机型不在机型表里（未识别）：
          {unrecognized.map((t, i) => (
            <span key={t.icaoCode}>
              {i > 0 ? "、" : ""}
              <Link href={`/flights?type=${encodeURIComponent(t.icaoCode)}`} className="font-mono font-medium text-primary">
                {t.icaoCode}
              </Link>
            </span>
          ))}
          。如果是填错了，可以在航班列表里修改。
        </p>
      ) : null}

      <div className="grid gap-8 lg:grid-cols-2">
        <Section title="窄体机和宽体机占比" note="按航班次数">
          <BodyShareBar counts={bodyShare} />
        </Section>
        <Section title="按年份的变化">
          <YearTrendChart byYear={byYear} />
        </Section>
      </div>

      <Section title="机型排行榜" note="按乘坐次数，前 10 名">
        <RankingChart types={types} />
      </Section>

      <Section title="机型收集墙" note={`坐过 ${types.length} 种机型，点开看详情`}>
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {types.map((t) => (
            <li key={t.icaoCode}>
              <button
                type="button"
                onClick={() => setSelected(t)}
                className={`flex h-full min-h-28 w-full flex-col rounded-xl bg-white p-3 text-left transition hover:ring-2 hover:ring-primary/40 ${
                  t.recognized ? "ring-1 ring-slate-200" : "border border-dashed border-slate-300"
                }`}
              >
                <span className="flex w-full items-start justify-between gap-2">
                  <span className="font-semibold leading-tight text-navy">{typeName(t)}</span>
                  {t.isNew ? <NewBadge /> : null}
                </span>
                <span className="mt-0.5 font-mono text-xs text-muted">{t.icaoCode}</span>
                <span className="mt-auto pt-2 text-sm tabular-nums text-navy">{t.flightCount} 次</span>
                <span className="text-xs text-muted">
                  {t.firstFlightDate} · {t.firstFlightNumber}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="按制造商" note="制造商 → 系列 → 型号，可以展开和折叠">
        <ul className="space-y-2">
          {hierarchy.map((m, i) => (
            <li key={m.name}>
              <details open={i === 0} className="rounded-xl bg-white ring-1 ring-slate-200">
                <summary className="flex min-h-12 cursor-pointer items-center justify-between px-4 font-semibold text-navy">
                  <span>{m.name}</span>
                  <span className="text-sm font-normal tabular-nums text-muted">{m.count} 次</span>
                </summary>
                <ul className="space-y-1 px-4 pb-3">
                  {m.families.map((f) => (
                    <li key={f.name}>
                      <details>
                        <summary className="flex min-h-11 cursor-pointer items-center justify-between rounded-lg px-2 text-navy hover:bg-slate-50">
                          <span>{f.name}</span>
                          <span className="text-sm tabular-nums text-muted">{f.count} 次</span>
                        </summary>
                        <ul className="ml-4 border-l border-slate-200 pl-3">
                          {f.models.map((model) => (
                            <li key={model.icaoCode}>
                              <button
                                type="button"
                                onClick={() => setSelected(model.stat)}
                                className="flex min-h-11 w-full items-center justify-between rounded-lg px-2 text-left text-sm hover:bg-slate-50"
                              >
                                <span>
                                  {model.model} <span className="font-mono text-xs text-muted">{model.icaoCode}</span>
                                </span>
                                <span className="tabular-nums text-muted">{model.count} 次</span>
                              </button>
                            </li>
                          ))}
                        </ul>
                      </details>
                    </li>
                  ))}
                </ul>
              </details>
            </li>
          ))}
        </ul>
      </Section>

      {selected ? <TypeDetail type={selected} onClose={() => setSelected(null)} /> : null}
    </div>
  );
}

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  const id = useId();
  return (
    <section aria-labelledby={id}>
      <div className="mb-3">
        <h2 id={id} className="text-lg font-semibold text-navy">
          {title}
        </h2>
        {note ? <p className="text-sm text-muted">{note}</p> : null}
      </div>
      {children}
    </section>
  );
}
