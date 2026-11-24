"use client";

import { BarChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import * as echarts from "echarts/core";
import type { EChartsCoreOption } from "echarts/core";
import { SVGRenderer } from "echarts/renderers";
import { useEffect, useRef } from "react";

echarts.use([BarChart, GridComponent, LegendComponent, TooltipComponent, SVGRenderer]);

/**
 * ECharts 的薄封装：按需引入（只打包用到的图表类型），SVG 渲染，跟随容器宽度自动缩放。
 * 图表本身对读屏软件是一张图（aria-label 说明内容），具体数值由旁边的数据表提供。
 */
export function EChart({ option, height, label }: { option: EChartsCoreOption; height: number; label: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const chart = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    if (!ref.current) return;
    const instance = echarts.init(ref.current, undefined, { renderer: "svg" });
    chart.current = instance;
    const observer = new ResizeObserver(() => instance.resize());
    observer.observe(ref.current);
    return () => {
      observer.disconnect();
      instance.dispose();
      chart.current = null;
    };
  }, []);

  useEffect(() => {
    chart.current?.setOption(option, true);
  }, [option]);

  return <div ref={ref} role="img" aria-label={label} style={{ height }} className="w-full" />;
}
