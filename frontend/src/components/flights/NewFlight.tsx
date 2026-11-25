"use client";

import { useRouter } from "next/navigation";
import { useId, useState, type KeyboardEvent } from "react";
import { Button } from "@/components/ui/Button";
import type { AiCandidate } from "@/lib/ai";
import { useAppConfig } from "@/lib/config";
import { todayLocal } from "@/lib/dates";
import type { LookupCandidate } from "@/lib/lookup";
import type { FlightInput, FlightSource } from "@/lib/types";
import { AiPanel } from "./AiPanel";
import { FlightForm } from "./FlightForm";
import { LookupPanel } from "./LookupPanel";

type Tab = "manual" | "lookup" | "ai";
const TABS: { id: Tab; label: string }[] = [
  { id: "manual", label: "手动填写" },
  { id: "lookup", label: "航班号查询" },
  { id: "ai", label: "AI 解析" },
];

type Prefill = {
  initial: Partial<FlightInput>;
  source: FlightSource;
  note?: string;
  seq: number;
  /** 机型框预填的是系列名，需要展开下拉框让用户选具体型号 */
  openAircraftList?: boolean;
};

/**
 * 录入航班：手动填写、航班号查询、AI 解析三种方式。查询和解析的结果都只预填同一个表单，用户确认后才保存。
 */
export function NewFlight() {
  const router = useRouter();
  const config = useAppConfig();
  const baseId = useId();
  const [tab, setTab] = useState<Tab>("manual");
  const [prefill, setPrefill] = useState<Prefill>({ initial: {}, source: "manual", seq: 0 });
  // AI 解析出的多条航班：逐条预填、确认（保存或跳过）后自动进入下一条
  const [queue, setQueue] = useState<{ items: AiCandidate[]; index: number } | null>(null);

  function showAiItem(items: AiCandidate[], index: number) {
    const c = items[index];
    setQueue({ items, index });
    setPrefill((p) => ({
      seq: p.seq + 1,
      source: "ai",
      initial: {
        flightDate: c.flightDate ?? undefined,
        flightNumber: c.flightNumber ?? "",
        depIata: c.depIata ?? "",
        arrIata: c.arrIata ?? "",
        aircraftType: c.aircraftType ?? c.aircraftSearch,
        registration: c.registration,
        notes: c.notes,
      },
      openAircraftList: !c.aircraftType && !!c.aircraftSearch,
      note: c.warnings.length > 0 ? `请特别核对：${c.warnings.join("；")}` : "已按 AI 解析结果预填，请核对后保存。",
    }));
    setTab("manual");
  }

  /** 当前这条处理完（保存或跳过）：还有下一条就预填下一条，否则回到航班列表。 */
  function nextOrFinish() {
    if (queue && queue.index + 1 < queue.items.length) {
      showAiItem(queue.items, queue.index + 1);
    } else {
      setQueue(null);
      router.push("/flights");
    }
  }

  function pickCandidate(c: LookupCandidate) {
    setQueue(null);
    setPrefill((p) => ({
      seq: p.seq + 1,
      source: "api",
      initial: {
        flightDate: c.flightDate,
        flightNumber: c.flightNumber,
        depIata: c.depIata,
        arrIata: c.arrIata,
        aircraftType: c.aircraftType ?? c.aircraftSearch,
        registration: c.registration,
      },
      openAircraftList: !c.aircraftType && !!c.aircraftSearch,
      note:
        !c.aircraftType && c.aircraftSearch
          ? `接口只返回了系列「${c.aircraftModel}」，请在「机型」下拉框里选择具体型号。`
          : !c.aircraftType && c.aircraftModel
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
            {queue ? (
              <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-primary/10 px-3 py-2 text-sm text-navy">
                <p>
                  <span className="font-semibold">
                    AI 解析结果 第 {queue.index + 1} / {queue.items.length} 条
                  </span>
                  ：{prefill.note}
                </p>
                <Button variant="ghost" onClick={nextOrFinish}>
                  跳过这条
                </Button>
              </div>
            ) : prefill.note ? (
              <p className="mb-4 rounded-lg bg-primary/10 px-3 py-2 text-sm text-navy">{prefill.note}</p>
            ) : null}
            <FlightForm
              key={prefill.seq}
              initial={prefill.initial}
              source={prefill.source}
              openAircraftList={prefill.openAircraftList}
              onSaved={nextOrFinish}
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
          <AiPanel onConfirm={(items) => showAiItem(items, 0)} />
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
