"use client";

import { useEffect, useId, useRef } from "react";
import { NewBadge } from "@/components/ui/NewBadge";
import { BODY_LABELS, typeName, type AircraftTypeStat } from "@/lib/aircraft-stats";
import { formatKm } from "@/lib/stats";

/** 机型详情（弹出层）：乘坐次数、总里程、首次乘坐、坐过的航司、注册号（同一架飞机坐过几次）。 */
export function TypeDetail({ type, onClose }: { type: AircraftTypeStat; onClose: () => void }) {
  const titleId = useId();
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center bg-navy/40 sm:items-center" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
        className="max-h-[85dvh] w-full overflow-y-auto rounded-t-2xl bg-white p-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] shadow-xl sm:max-w-md sm:rounded-2xl"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id={titleId} className="text-lg font-bold text-navy">
              {typeName(type)}
            </h2>
            <p className="mt-0.5 text-sm text-muted">
              <span className="font-mono">{type.icaoCode}</span>
              {type.recognized && type.body ? ` · ${BODY_LABELS[type.body]}` : " · 不在机型表里"}
            </p>
          </div>
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            aria-label="关闭"
            className="flex size-11 shrink-0 items-center justify-center rounded-lg text-xl text-muted hover:bg-slate-100"
          >
            ×
          </button>
        </div>
        {type.isNew ? (
          <div className="mt-2">
            <NewBadge />
          </div>
        ) : null}

        <dl className="mt-4 grid grid-cols-3 gap-3 text-center">
          <div className="rounded-lg bg-slate-50 p-2">
            <dt className="text-xs text-muted">乘坐次数</dt>
            <dd className="text-lg font-bold tabular-nums text-navy">{type.flightCount}</dd>
          </div>
          <div className="rounded-lg bg-slate-50 p-2">
            <dt className="text-xs text-muted">总里程</dt>
            <dd className="text-sm font-bold tabular-nums text-navy">{formatKm(type.distanceKm)}</dd>
          </div>
          <div className="rounded-lg bg-slate-50 p-2">
            <dt className="text-xs text-muted">首次乘坐</dt>
            <dd className="text-sm font-bold tabular-nums text-navy">{type.firstFlightDate}</dd>
          </div>
        </dl>

        <h3 className="mt-5 text-sm font-semibold text-navy">坐过的航司</h3>
        {type.airlines.length === 0 ? (
          <p className="mt-1 text-sm text-muted">无</p>
        ) : (
          <ul className="mt-1 divide-y divide-slate-100 text-sm">
            {type.airlines.map((a) => (
              <li key={a.iata} className="flex justify-between py-1.5">
                <span>
                  <span className="font-mono text-muted">{a.iata}</span> {a.name ?? ""}
                </span>
                <span className="tabular-nums text-muted">{a.count} 次</span>
              </li>
            ))}
          </ul>
        )}

        <h3 className="mt-5 text-sm font-semibold text-navy">注册号</h3>
        {type.registrations.length === 0 ? (
          <p className="mt-1 text-sm text-muted">没有记录注册号</p>
        ) : (
          <ul className="mt-1 divide-y divide-slate-100 text-sm">
            {type.registrations.map((r) => (
              <li key={r.registration} className="flex justify-between py-1.5">
                <span className="font-mono">{r.registration}</span>
                <span className={`tabular-nums ${r.count > 1 ? "font-semibold text-navy" : "text-muted"}`}>
                  {r.count} 次
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
