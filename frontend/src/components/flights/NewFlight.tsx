"use client";

import { useRouter } from "next/navigation";
import { useId, useState, type KeyboardEvent } from "react";
import { useAppConfig } from "@/lib/config";
import { todayLocal } from "@/lib/dates";
import type { LookupCandidate } from "@/lib/lookup";
import type { FlightInput, FlightSource } from "@/lib/types";
import { FlightForm } from "./FlightForm";
import { LookupPanel } from "./LookupPanel";

type Tab = "manual" | "lookup" | "ai";
const TABS: { id: Tab; label: string }[] = [
  { id: "manual", label: "手动填写" },
  { id: "lookup", label: "航班号查询" },
  { id: "ai", label: "AI 解析" },
];

type Prefill = { initial: Partial<FlightInput>; source: FlightSource; note?: string; seq: number };

/**
 * 录入航班：手动填写、航班号查询、AI 解析三种方式。查询和解析的结果都只预填同一个表单，用户确认后才保存。
 */
export function NewFlight() {
  const router = useRouter();
  const config = useAppConfig();
  const baseId = useId();
  const [tab, setTab] = useState<Tab>("manual");
  const [prefill, setPrefill] = useState<Prefill>({ initial: {}, source: "manual", seq: 0 });

  function pickCandidate(c: LookupCandidate) {
    setPrefill((p) => ({
      seq: p.seq + 1,
      source: "api",
      initial: {
        flightDate: c.flightDate,
        flightNumber: c.flightNumber,
        depIata: c.depIata,
        arrIata: c.arrIata,
        aircraftType: c.aircraftType,
        registration: c.registration,
      },
      note:
        !c.aircraftType && c.aircraftModel
          ? `接口返回的机型「${c.aircraftModel}」没能匹配到机型代码，请在「机型」里手动选择。`
          : "已按查询结果预填，请核对后保存。",
    }));
    setTab("manual");
  }

  function onTabKey(event: KeyboardEvent, index: number) {
    const delta = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (delta) {
      event.preventDefault();
      setTab(TABS[(index + delta + TABS.length) % TABS.length].id);
    }
  }

  return (
    <div className="rounded-xl bg-white ring-1 ring-slate-200">
      <div role="tablist" aria-label="录入方式" className="flex border-b border-slate-200">
        {TABS.map((t, i) => (
          <button
            key={t.id}
            id={`${baseId}-tab-${t.id}`}
            role="tab"
            type="button"
            aria-selected={tab === t.id}
            aria-controls={`${baseId}-panel`}
            tabIndex={tab === t.id ? 0 : -1}
            onClick={() => setTab(t.id)}
            onKeyDown={(e) => onTabKey(e, i)}
            className={`min-h-12 flex-1 border-b-2 px-3 text-sm font-medium ${
              tab === t.id ? "border-primary text-primary" : "border-transparent text-muted hover:text-navy"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div id={`${baseId}-panel`} role="tabpanel" aria-labelledby={`${baseId}-tab-${tab}`} className="p-4 md:p-6">
        {tab === "manual" ? (
          <>
            {prefill.note ? (
              <p className="mb-4 rounded-lg bg-primary/10 px-3 py-2 text-sm text-navy">{prefill.note}</p>
            ) : null}
            <FlightForm
              key={prefill.seq}
              initial={prefill.initial}
              source={prefill.source}
              onSaved={() => router.push("/flights")}
              onCancel={() => router.back()}
            />
          </>
        ) : tab === "lookup" ? (
          config.data?.lookupEnabled ? (
            <LookupPanel today={todayLocal()} onPick={pickCandidate} />
          ) : (
            <Unavailable text="没有配置 AeroDataBox API key，航班号查询不可用。请在项目根目录的 .env 里填写 AERODATABOX_API_KEY 后重启，或者手动填写。" />
          )
        ) : config.data?.aiEnabled ? (
          <Unavailable text="AI 解析将在下一阶段加入。" />
        ) : (
          <Unavailable text="没有配置 Gemini API key，AI 解析不可用。请在 .env 里填写 GEMINI_API_KEY 后重启，或者手动填写。" />
        )}
      </div>
    </div>
  );
}

function Unavailable({ text }: { text: string }) {
  return <p className="rounded-lg bg-slate-50 px-3 py-3 text-sm text-muted">{text}</p>;
}
