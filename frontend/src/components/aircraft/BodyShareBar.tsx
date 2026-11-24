import { shareSegments, type BodyCounts, type BodyKey } from "@/lib/aircraft-stats";

/** 机身类型的背景色类名（Tailwind 主题变量 --color-body-*）。 */
export const BODY_BG: Record<BodyKey, string> = {
  narrow: "bg-body-narrow",
  wide: "bg-body-wide",
  regional: "bg-body-regional",
  unknown: "bg-body-unknown",
};

/**
 * 窄体 / 宽体 / 支线占比：一条分段条（比饼图更容易比较长度）。
 * 段之间留 2px 空隙；百分比放得下才写在段上，图例里总有名称、次数和百分比。
 */
export function BodyShareBar({ counts }: { counts: BodyCounts }) {
  const segments = shareSegments(counts);
  if (segments.length === 0) return null;
  return (
    <div>
      <div className="flex h-10 w-full gap-0.5 overflow-hidden rounded-lg" aria-hidden="true">
        {segments.map((s) => (
          <div
            key={s.key}
            className={`flex items-center justify-center text-sm font-semibold text-white ${BODY_BG[s.key]}`}
            style={{ width: `${s.percent}%`, minWidth: 4 }}
          >
            {s.percent >= 12 ? `${s.percent}%` : null}
          </div>
        ))}
      </div>
      <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-sm">
        {segments.map((s) => (
          <li key={s.key} className="flex items-center gap-2">
            <span className={`size-3 rounded-sm ${BODY_BG[s.key]}`} aria-hidden="true" />
            <span className="text-navy">
              {s.label} {s.count} 次 · {s.percent}%
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
