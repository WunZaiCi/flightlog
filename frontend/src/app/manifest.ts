import type { MetadataRoute } from "next";

// 静态导出时在构建阶段生成 manifest.webmanifest
export const dynamic = "force-static";

/**
 * PWA manifest：手机浏览器里「添加到主屏幕」后像独立 App 一样打开。
 * 颜色值与 globals.css 的 --color-navy 一致（manifest 里不能用 CSS 变量）。
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "FlightLog 航班记录",
    short_name: "FlightLog",
    description: "记录坐过的航班，统计机型，画出飞过的航线",
    lang: "zh-CN",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#032147",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
