import type { NextConfig } from "next";
import { PHASE_DEVELOPMENT_SERVER } from "next/constants";

/** 开发时 /api 代理到的后端地址 */
const BACKEND_URL = process.env.FLIGHTLOG_BACKEND_URL ?? "http://localhost:8000";

/**
 * - 构建（next build）：静态导出到 out/，由 Spring Boot 托管，前端和 API 同源。
 * - 开发（next dev）：不开静态导出（静态导出模式下 rewrites 会报错），把 /api 代理到后端。
 */
export default function nextConfig(phase: string): NextConfig {
  const isDev = phase === PHASE_DEVELOPMENT_SERVER;

  return {
    ...(isDev ? {} : { output: "export" as const }),
    // 产出 flights.html、flights/new.html，与后端的回退规则对应
    trailingSlash: false,
    images: { unoptimized: true },
    turbopack: {
      rules: {
        "*.css": {
          loaders: ["@tailwindcss/turbopack"],
          as: "*.css",
        },
      },
    },
    ...(isDev
      ? {
          async rewrites() {
            return [{ source: "/api/:path*", destination: `${BACKEND_URL}/api/:path*` }];
          },
        }
      : {}),
  };
}
