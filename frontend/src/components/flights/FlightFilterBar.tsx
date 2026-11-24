"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useId, useState } from "react";
import { Button } from "@/components/ui/Button";
import { activeFilterCount, toQueryString, type Facets, type FlightFilters } from "@/lib/flight-filters";
import { useDebounced } from "@/lib/use-debounced";

/**
 * 航班列表的筛选和搜索。条件保存在 URL 里（刷新、分享都保留）。
 * 手机上筛选器收进可展开的面板，搜索框一直显示。
 */
export function FlightFilterBar({ filters, options }: { filters: FlightFilters; options: Facets }) {
  const router = useRouter();
  const pathname = usePathname();
  const panelId = useId();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState(filters.q ?? "");
  const debouncedSearch = useDebounced(search, 300);

  function apply(next: FlightFilters) {
    const qs = toQueryString(next);
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  }

  useEffect(() => {
    if ((debouncedSearch.trim() || undefined) !== (filters.q || undefined)) {
      apply({ ...filters, q: debouncedSearch });
    }
    // 只在搜索词稳定后同步到 URL
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch]);

  const set = (key: keyof FlightFilters) => (value: string) => apply({ ...filters, [key]: value });
  const count = activeFilterCount(filters);
  const selectCount = activeFilterCount({ ...filters, q: undefined });

  return (
    <div className="mb-4 space-y-3">
      <div className="flex gap-2">
        <input
          type="search"
          aria-label="搜索"
          placeholder="搜索航班号、注册号、城市、机型、备注"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="block min-h-11 w-full min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-3 text-base outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
        />
        <Button
          variant="ghost"
          className="ring-1 ring-slate-300 md:hidden"
          aria-expanded={open}
          aria-controls={panelId}
          onClick={() => setOpen((v) => !v)}
        >
          {selectCount > 0 ? `筛选（${selectCount}）` : "筛选"}
        </Button>
      </div>

      <div id={panelId} className={`${open ? "grid" : "hidden"} gap-3 sm:grid-cols-2 md:grid md:grid-cols-4`}>
        <Select label="年份" value={filters.year} onChange={set("year")} options={options.years.map((y) => ({ value: y, label: `${y} 年` }))} />
        <Select label="航司" value={filters.airline} onChange={set("airline")} options={options.airlines} />
        <Select label="机型" value={filters.type} onChange={set("type")} options={options.types} />
        <Select label="机场" value={filters.airport} onChange={set("airport")} options={options.airports} />
      </div>

      {count > 0 ? (
        <div className="flex justify-end">
          <Button
            variant="ghost"
            onClick={() => {
              setSearch("");
              apply({});
            }}
          >
            清除筛选
          </Button>
        </div>
      ) : null}
    </div>
  );
}

function Select({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value?: string;
  onChange: (value: string) => void;
  options: { value: string; label: string; count?: number }[];
}) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-navy">
        {label}
      </label>
      <select
        id={id}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        className="block min-h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-base outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
      >
        <option value="">全部</option>
        {/* URL 里的值可能不在选项里（例如数据已删除），也要能显示和清除 */}
        {value && !options.some((o) => o.value === value) ? <option value={value}>{value}</option> : null}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.count !== undefined ? `${o.label}（${o.count}）` : o.label}
          </option>
        ))}
      </select>
    </div>
  );
}
