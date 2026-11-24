"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { NewBadge } from "@/components/ui/NewBadge";
import { Spinner } from "@/components/ui/Spinner";
import { earthLaps, formatKm, useSummary, type Summary } from "@/lib/stats";

/** 首页总览：总数、总里程、最近 5 次航班、新解锁的机型。 */
export function Overview() {
  const summary = useSummary();

  if (summary.isPending) return <Spinner />;
  if (summary.isError) {
    return (
      <div role="alert" className="space-y-3 text-center">
        <p>{summary.error.message}</p>
        <Button onClick={() => summary.refetch()}>重试</Button>
      </div>
    );
  }

  const s = summary.data;
  if (s.flightCount === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center">
        <p className="text-lg font-semibold text-navy">还没有航班记录</p>
        <p className="mt-1 text-sm text-muted">录入坐过的航班后，这里会显示机型、航司、机场和里程的统计。</p>
        <Link
          href="/flights/new"
          className="mt-4 inline-flex min-h-11 items-center rounded-lg bg-primary px-4 text-sm font-semibold text-white"
        >
          录入第一个航班
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <StatTiles summary={s} />
      <div className="grid gap-8 lg:grid-cols-2">
        <RecentFlights summary={s} />
        <NewlyUnlocked summary={s} />
      </div>
    </div>
  );
}

function StatTiles({ summary: s }: { summary: Summary }) {
  return (
    <ul aria-label="统计" className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      <Tile label="航班" value={s.flightCount.toLocaleString("en-US")} />
      <Tile
        label="机型"
        value={s.aircraftTypeCount}
        note={s.unrecognizedTypeCount > 0 ? `${s.unrecognizedTypeCount} 个未识别` : undefined}
      />
      <Tile label="航司" value={s.airlineCount} />
      <Tile label="机场" value={s.airportCount} />
      <Tile
        label="总里程"
        value={formatKm(s.totalDistanceKm)}
        note={`绕地球 ${earthLaps(s.totalDistanceKm).toFixed(2)} 圈`}
        className="col-span-2 sm:col-span-1"
      />
    </ul>
  );
}

function Tile({ label, value, note, className = "" }: { label: string; value: ReactNode; note?: string; className?: string }) {
  return (
    <li className={`rounded-xl bg-white p-4 ring-1 ring-slate-200 ${className}`}>
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-1 text-2xl font-bold tabular-nums text-navy">{value}</p>
      {note ? <p className="mt-0.5 text-xs text-muted">{note}</p> : null}
    </li>
  );
}

function Section({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  const id = `section-${title}`;
  return (
    <section aria-labelledby={id}>
      <div className="mb-3 flex items-baseline justify-between">
        <h2 id={id} className="text-lg font-semibold text-navy">
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function RecentFlights({ summary: s }: { summary: Summary }) {
  return (
    <Section
      title="最近的航班"
      action={
        <Link href="/flights" className="text-sm font-medium text-primary">
          查看全部
        </Link>
      }
    >
      <ul className="divide-y divide-slate-100 rounded-xl bg-white ring-1 ring-slate-200">
        {s.recentFlights.map((f) => (
          <li key={f.id} className="flex min-h-14 items-center gap-3 px-4 py-2 text-sm">
            <time dateTime={f.flightDate} className="w-24 shrink-0 tabular-nums text-muted">
              {f.flightDate}
            </time>
            <span className="w-16 shrink-0 font-mono font-semibold text-navy">{f.flightNumber}</span>
            <span className="min-w-0 flex-1 truncate">
              {f.dep.iata} → {f.arr.iata}
            </span>
            <span className="hidden truncate text-muted sm:inline">
              {f.aircraftType?.displayName ?? f.aircraftTypeIcao ?? ""}
            </span>
          </li>
        ))}
      </ul>
    </Section>
  );
}

function NewlyUnlocked({ summary: s }: { summary: Summary }) {
  return (
    <Section title="新解锁的机型">
      {s.newlyUnlocked.length === 0 ? (
        <p className="rounded-xl bg-white p-4 text-sm text-muted ring-1 ring-slate-200">最近 90 天没有坐到新机型</p>
      ) : (
        <ul className="space-y-2">
          {s.newlyUnlocked.map((t) => (
            <li key={t.icaoCode} className="flex items-center gap-3 rounded-xl bg-white p-3 ring-1 ring-slate-200">
              <NewBadge />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-navy">
                  {t.recognized ? t.displayName : `${t.icaoCode}（未识别）`}
                </p>
                <p className="text-xs text-muted">
                  {t.firstFlightDate} · {t.firstFlightNumber} 首次乘坐
                </p>
              </div>
              <span className="font-mono text-xs text-muted">{t.icaoCode}</span>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}
