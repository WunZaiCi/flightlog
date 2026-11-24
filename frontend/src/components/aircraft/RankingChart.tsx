"use client";

import { useMemo } from "react";
import { EChart } from "@/components/charts/EChart";
import { typeName, type AircraftTypeStat } from "@/lib/aircraft-stats";
import { themeColor } from "@/lib/theme";

const MAX_ROWS = 10;
const ROW_HEIGHT = 36;

/** 机型排行榜：横向条形图，按乘坐次数，单一系列（主蓝色），数值写在条的末端。 */
export function RankingChart({ types }: { types: AircraftTypeStat[] }) {
  const top = types.slice(0, MAX_ROWS);
  const label = `机型排行榜：${top.map((t) => `${typeName(t)} ${t.flightCount} 次`).join("，")}`;

  const option = useMemo(() => {
    const rows = [...top].reverse(); // ECharts 的类目轴从下往上排，反过来让第一名在最上面
    const text = themeColor("navy");
    const muted = themeColor("muted");
    return {
      animation: false,
      grid: { left: 8, right: 40, top: 8, bottom: 8, containLabel: true },
      xAxis: { type: "value", show: false, minInterval: 1 },
      yAxis: {
        type: "category",
        data: rows.map((t) => typeName(t)),
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { color: text, fontSize: 12, width: 140, overflow: "truncate" },
      },
      tooltip: {
        trigger: "item",
        formatter: (p: { name: string; value: number }) => `${p.name}<br/>${p.value} 次`,
      },
      series: [
        {
          type: "bar",
          data: rows.map((t) => t.flightCount),
          barMaxWidth: 24,
          itemStyle: { color: themeColor("primary"), borderRadius: [0, 4, 4, 0] },
          label: { show: true, position: "right", color: muted, fontSize: 12 },
          emphasis: { itemStyle: { color: themeColor("navy") } },
        },
      ],
    };
    // top 由 types 决定
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [types]);

  return <EChart option={option} height={top.length * ROW_HEIGHT + 16} label={label} />;
}
