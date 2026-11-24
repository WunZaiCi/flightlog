"use client";

import Link from "next/link";
import { useId, useMemo, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { facets } from "@/lib/flight-filters";
import { useFlights } from "@/lib/flights";
import { routeKey, useRoutes, type MapFilters, type RoutesData } from "@/lib/routes";
import { earthLaps, formatKm } from "@/lib/stats";
import { RouteMap } from "./RouteMap";

type Selection = { kind: "route"; key: string } | { kind: "airport"; iata: string } | null;

/**
 * 航线地图页。桌面：地图 + 右侧栏；手机：地图全屏，筛选、统计和详情收进底部抽屉。
 */
export function MapPage() {
  const [filters, setFilters] = useState<MapFilters>({});
  const [selection, setSelection] = useState<Selection>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const routes = useRoutes(filters);
  const all = useFlights();
  const options = useMemo(() => facets(all.data ?? []), [all.data]);

  const select = (s: Selection) => {
    setSelection(s);
    if (s) setDrawerOpen(true);
  };

  const data = routes.data;
  const sidebar = data ? (
    <Sidebar
      data={data}
      filters={filters}
      options={options}
      onFilter={(f) => {
        setFilters(f);
        setSelection(null);
      }}
      selection={selection}
      onSelect={select}
    />
  ) : null;

  return (
    <div className="relative -mx-4 -mt-6 -mb-[calc(5rem+env(safe-area-inset-bottom))] h-[calc(100dvh-7rem-env(safe-area-inset-top)-env(safe-area-inset-bottom))] md:mx-0 md:mt-0 md:mb-0 md:flex md:h-[calc(100dvh-12rem)] md:min-h-[480px] md:gap-4">
      <div className="relative h-full flex-1 overflow-hidden md:rounded-xl md:ring-1 md:ring-slate-200">
        {routes.isPending ? (
          <div className="flex h-full items-center justify-center">
            <Spinner />
          </div>
        ) : routes.isError ? (
          <div role="alert" className="flex h-full flex-col items-center justify-center gap-3">
            <p>{routes.error.message}</p>
            <Button onClick={() => routes.refetch()}>重试</Button>
          </div>
        ) : (
          <RouteMap
            routes={data!.routes}
            airports={data!.airports}
            selectedRoute={selection?.kind === "route" ? selection.key : null}
            selectedAirport={selection?.kind === "airport" ? selection.iata : null}
            onSelectRoute={(key) => select({ kind: "route", key })}
            onSelectAirport={(iata) => select({ kind: "airport", iata })}
          />
        )}
        {data && data.totals.flightCount === 0 ? (
          <p className="pointer-events-none absolute inset-x-4 top-4 rounded-lg bg-white/95 p-3 text-center text-sm text-muted shadow md:inset-x-auto md:left-1/2 md:-translate-x-1/2">
            还没有航班，录入后会在地图上画出航线
          </p>
        ) : null}
        <Button
          className="absolute top-3 right-3 shadow md:hidden"
          variant="primary"
          aria-expanded={drawerOpen}
          onClick={() => setDrawerOpen((v) => !v)}
        >
          筛选和统计
        </Button>
      </div>

      {/* 侧栏：桌面固定在右侧；手机是底部抽屉（点「筛选和统计」或选中航线、机场时打开） */}
      <aside
        aria-label="地图侧栏"
        className={`${
          drawerOpen
            ? "fixed inset-x-0 bottom-0 z-30 block max-h-[70dvh] rounded-t-2xl bg-white p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] shadow-2xl"
            : "hidden"
        } overflow-y-auto md:static md:block md:max-h-none md:w-80 md:shrink-0 md:rounded-none md:bg-transparent md:p-0 md:shadow-none`}
      >
        <div className="mb-2 flex justify-end md:hidden">
          <Button variant="ghost" onClick={() => setDrawerOpen(false)}>
            收起
          </Button>
        </div>
        {sidebar}
      </aside>
    </div>
  );
}

type SidebarProps = {
  data: RoutesData;
  filters: MapFilters;
  options: ReturnType<typeof facets>;
  onFilter: (f: MapFilters) => void;
  selection: Selection;
  onSelect: (s: Selection) => void;
};

function Sidebar({ data, filters, options, onFilter, selection, onSelect }: SidebarProps) {
  const { totals } = data;
  return (
    <div className="space-y-6">
      {selection ? <SelectionDetail data={data} selection={selection} onSelect={onSelect} /> : null}

      <Panel title="筛选">
        <div className="grid grid-cols-3 gap-2 md:grid-cols-1">
          <FilterSelect label="年份" value={filters.year} options={options.years.map((y) => ({ value: y, label: `${y} 年` }))} onChange={(v) => onFilter({ ...filters, year: v })} />
          <FilterSelect label="机型" value={filters.type} options={options.types} onChange={(v) => onFilter({ ...filters, type: v })} />
          <FilterSelect label="航司" value={filters.airline} options={options.airlines} onChange={(v) => onFilter({ ...filters, airline: v })} />
        </div>
      </Panel>

      <Panel title="航线统计">
        <dl className="grid grid-cols-2 gap-x-3 gap-y-2 text-sm">
          <Stat label="航班" value={String(totals.flightCount)} />
          <Stat label="国家和地区" value={String(totals.countryCount)} />
          <Stat label="总里程" value={formatKm(totals.totalDistanceKm)} note={`绕地球 ${earthLaps(totals.totalDistanceKm).toFixed(2)} 圈`} wide />
          {totals.longest ? (
            <Stat label="最长" value={`${totals.longest.from} ⇄ ${totals.longest.to} · ${formatKm(totals.longest.distanceKm)}`} wide />
          ) : null}
          {totals.shortest ? (
            <Stat label="最短" value={`${totals.shortest.from} ⇄ ${totals.shortest.to} · ${formatKm(totals.shortest.distanceKm)}`} wide />
          ) : null}
        </dl>
      </Panel>

      {data.routes.length > 0 ? (
        <Panel title="航线">
          <ul className="space-y-1">
            {data.routes.map((r) => {
              const key = routeKey(r);
              const active = selection?.kind === "route" && selection.key === key;
              return (
                <li key={key}>
                  <button
                    type="button"
                    onClick={() => onSelect({ kind: "route", key })}
                    aria-pressed={active}
                    className={`flex min-h-11 w-full items-center justify-between rounded-lg px-2 text-left text-sm ${active ? "bg-accent/20" : "hover:bg-slate-50"}`}
                  >
                    <span className="font-mono font-semibold text-navy">
                      {r.from} ⇄ {r.to}
                    </span>
                    <span className="tabular-nums text-muted">
                      {r.count} 次 · {formatKm(r.distanceKm)}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </Panel>
      ) : null}
    </div>
  );
}

function SelectionDetail({ data, selection, onSelect }: { data: RoutesData; selection: NonNullable<Selection>; onSelect: (s: Selection) => void }) {
  if (selection.kind === "route") {
    const route = data.routes.find((r) => routeKey(r) === selection.key);
    if (!route) return null;
    return (
      <Panel title={`${route.from} ⇄ ${route.to}`} note={`${route.count} 次 · ${formatKm(route.distanceKm)}`} onClose={() => onSelect(null)}>
        <ul className="divide-y divide-slate-100 text-sm">
          {route.flights.map((f) => (
            <li key={f.id} className="flex items-center justify-between gap-2 py-2">
              <div className="min-w-0">
                <p>
                  <span className="font-mono font-semibold text-navy">{f.flightNumber}</span>{" "}
                  <span className="text-muted">
                    {f.depIata} → {f.arrIata}
                  </span>
                </p>
                <p className="truncate text-xs text-muted">
                  {f.flightDate}
                  {f.aircraft ? ` · ${f.aircraft}` : ""}
                  {f.airlineName ? ` · ${f.airlineName}` : ""}
                </p>
              </div>
              <Link href={`/flights/edit?id=${f.id}`} aria-label={`编辑 ${f.flightNumber}`} className="inline-flex min-h-11 shrink-0 items-center px-2 text-primary">
                编辑
              </Link>
            </li>
          ))}
        </ul>
      </Panel>
    );
  }

  const airport = data.airports.find((a) => a.iata === selection.iata);
  if (!airport) return null;
  const airportRoutes = data.routes.filter((r) => r.from === airport.iata || r.to === airport.iata);
  return (
    <Panel title={`${airport.iata} · ${airport.city ?? airport.name}`} onClose={() => onSelect(null)}>
      <p className="text-sm text-navy">{airport.name}</p>
      <p className="text-xs text-muted">{[airport.city, airport.countryCode].filter(Boolean).join(", ")}</p>
      <dl className="mt-3 grid grid-cols-3 gap-2 text-sm">
        <Stat label="到访" value={String(airport.visits)} />
        <Stat label="出发" value={String(airport.departures)} />
        <Stat label="到达" value={String(airport.arrivals)} />
      </dl>
      <ul className="mt-3 space-y-1">
        {airportRoutes.map((r) => (
          <li key={routeKey(r)}>
            <button
              type="button"
              onClick={() => onSelect({ kind: "route", key: routeKey(r) })}
              className="flex min-h-11 w-full items-center justify-between rounded-lg px-2 text-left text-sm hover:bg-slate-50"
            >
              <span className="font-mono">
                {r.from} ⇄ {r.to}
              </span>
              <span className="text-muted">{r.count} 次</span>
            </button>
          </li>
        ))}
      </ul>
    </Panel>
  );
}

function Panel({ title, note, onClose, children }: { title: string; note?: string; onClose?: () => void; children: ReactNode }) {
  const id = useId();
  return (
    <section aria-labelledby={id} className="rounded-xl bg-white p-3 ring-1 ring-slate-200">
      <div className="mb-2 flex items-start justify-between gap-2">
        <div>
          <h2 id={id} className="font-semibold text-navy">
            {title}
          </h2>
          {note ? <p className="text-xs text-muted">{note}</p> : null}
        </div>
        {onClose ? (
          <button type="button" onClick={onClose} aria-label="关闭详情" className="flex size-11 items-center justify-center rounded-lg text-xl text-muted hover:bg-slate-100">
            ×
          </button>
        ) : null}
      </div>
      {children}
    </section>
  );
}

function Stat({ label, value, note, wide }: { label: string; value: string; note?: string; wide?: boolean }) {
  return (
    <div className={wide ? "col-span-full" : undefined}>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="font-semibold tabular-nums text-navy">{value}</dd>
      {note ? <dd className="text-xs text-muted">{note}</dd> : null}
    </div>
  );
}

function FilterSelect({ label, value, options, onChange }: { label: string; value?: string; options: { value: string; label: string }[]; onChange: (v: string) => void }) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-xs text-muted">
        {label}
      </label>
      <select
        id={id}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        className="block min-h-11 w-full rounded-lg border border-slate-300 bg-white px-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
      >
        <option value="">全部</option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}
