"use client";

import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { ApiError } from "@/lib/api";
import { lookupFlight, type LookupCandidate } from "@/lib/lookup";

/**
 * 航班号 + 日期 → AeroDataBox（后端带缓存）→ 用户选一条结果预填表单。
 * 免费额度有限：只在点「查询」时请求，不随输入自动查询。
 */
export function LookupPanel({ today, onPick }: { today: string; onPick: (c: LookupCandidate) => void }) {
  const [number, setNumber] = useState("");
  const [date, setDate] = useState(today);
  const search = useMutation({ mutationFn: () => lookupFlight(number, date) });
  const error = search.error instanceof ApiError ? search.error : null;

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    search.mutate();
  }

  return (
    <div className="space-y-4">
      <form onSubmit={onSubmit} noValidate className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
        <Field
          label="航班号"
          required
          autoCapitalize="characters"
          autoComplete="off"
          placeholder="例如 NH242"
          value={number}
          onChange={(e) => setNumber(e.target.value)}
          error={error?.fieldErrors.number}
        />
        <Field label="日期" type="date" required value={date} onChange={(e) => setDate(e.target.value)} error={error?.fieldErrors.date} />
        <Button type="submit" loading={search.isPending} disabled={!number.trim()}>
          查询
        </Button>
      </form>

      {error && Object.keys(error.fieldErrors).length === 0 ? (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {error.message}
        </p>
      ) : null}

      {search.data ? (
        <section aria-label="查询结果" className="space-y-2">
          {search.data.candidates.map((c) => (
            <div key={`${c.depIata}-${c.arrIata}-${c.flightDate}`} className="flex flex-wrap items-center justify-between gap-3 rounded-xl p-3 ring-1 ring-slate-200">
              <div className="min-w-0 text-sm">
                <p className="font-semibold text-navy">
                  <span className="font-mono">{c.flightNumber}</span> · {c.flightDate}
                </p>
                <p className="font-mono text-navy">
                  {c.depIata} → {c.arrIata}
                </p>
                <p className="text-muted">
                  {[c.airlineName, c.aircraftModel, c.registration].filter(Boolean).join(" · ") || "没有机型信息"}
                </p>
              </div>
              <Button variant="primary" onClick={() => onPick(c)}>
                使用这条
              </Button>
            </div>
          ))}
          <p className="text-xs text-muted">{search.data.cached ? "结果来自缓存，没有消耗查询额度" : "查询结果已缓存，同一航班再查不消耗额度"}</p>
        </section>
      ) : null}
    </div>
  );
}
