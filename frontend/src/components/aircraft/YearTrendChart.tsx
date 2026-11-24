"use client";

import { useMemo } from "react";
import { EChart } from "@/components/charts/EChart";
import { BODY_KEYS, BODY_LABELS, type AircraftStats, type BodyKey } from "@/lib/aircraft-stats";
import { themeColor } from "@/lib/theme";

/** 每年坐的窄体 / 宽体 / 支线航班数：堆叠柱状图，下面附数据表。 */
export function YearTrendChart({ byYear }: { byYear: AircraftStats["byYear"] }) {
  // 只画出现过的类型；颜色按类型固定，不随出现的类型数量变化
  const keys = BODY_KEYS.filter((k) => byYear.some((y) => y[k] > 0));

  const option = useMemo(() => {
    const surface = themeColor("surface");
    const muted = themeColor("muted");
    return {
      animation: false,
      grid: { left: 8, right: 8, top: 40, bottom: 8, containLabel: true },
      legend: { top: 0, left: 0, icon: "roundRect", itemWidth: 12, itemHeight: 12, textStyle: { color: themeColor("navy") } },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: {
        type: "category",
        data: byYear.map((y) => y.year),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: themeColor("chart-grid") } },
        axisLabel: { color: muted },
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: muted },
        splitLine: { lineStyle: { color: themeColor("chart-grid"), width: 1, type: "solid" } },
      },
      series: keys.map((k: BodyKey) => ({
        name: BODY_LABELS[k],
        type: "bar",
        stack: "body",
        barMaxWidth: 24,
        data: byYear.map((y) => y[k]),
        // 段与段之间用表面色隔开（2px 空隙）
        itemStyle: { color: themeColor(`body-${k}`), borderColor: surface, borderWidth: 1 },
      })),
    };
    // keys 由 byYear 决定
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [byYear]);

  return (
    <>
      <EChart option={option} height={260} label="每年乘坐的窄体、宽体、支线航班数（数据见下表）" />
      <details className="mt-2 text-sm">
        <summary className="inline-flex min-h-11 cursor-pointer items-center text-primary">查看数据表</summary>
        <div className="overflow-x-auto">
          <table className="mt-2 w-full text-left tabular-nums">
            <thead className="text-muted">
              <tr>
                <th className="py-1 pr-4 font-normal">年份</th>
                {keys.map((k) => (
                  <th key={k} className="py-1 pr-4 font-normal">
                    {BODY_LABELS[k]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {byYear.map((y) => (
                <tr key={y.year} className="border-t border-slate-100">
                  <td className="py-1 pr-4">{y.year}</td>
                  {keys.map((k) => (
                    <td key={k} className="py-1 pr-4">
                      {y[k]}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </>
  );
}
