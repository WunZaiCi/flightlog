// 生成 PWA 和网站图标：深海军蓝底 + 黄色飞机（与导航栏 Logo 一致）。
// 用 Playwright 的 Chromium 把 SVG 截图成 PNG，不引入额外依赖。图标改动时运行：node scripts/generate-icons.mjs
// 颜色与 globals.css 的 --color-navy、--color-accent 一致。
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const NAVY = "#032147";
const ACCENT = "#ecad0a";
// 与 src/components/Icon.tsx 的 plane 图标相同的路径
const PLANE =
  "M21 15.5v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V8.5l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-6z";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

/** padding：飞机四周留白占边长的比例（maskable 图标要留出安全区）。radius：圆角占边长的比例。 */
function svg(size, { padding, radius }) {
  const inner = size * (1 - padding * 2);
  const scale = inner / 24;
  const offset = size * padding;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
  <rect width="${size}" height="${size}" rx="${size * radius}" fill="${NAVY}"/>
  <g transform="translate(${offset} ${offset}) scale(${scale})">
    <path d="${PLANE}" fill="${ACCENT}" stroke="${ACCENT}" stroke-width="0.6" stroke-linejoin="round"/>
  </g>
</svg>`;
}

const outputs = [
  { file: "public/icons/icon-192.png", size: 192, padding: 0.18, radius: 0.22 },
  { file: "public/icons/icon-512.png", size: 512, padding: 0.18, radius: 0.22 },
  // maskable：系统会裁成圆形或其他形状，背景铺满，飞机放在中间 60% 的安全区内
  { file: "public/icons/icon-maskable-512.png", size: 512, padding: 0.26, radius: 0 },
  // iOS 主屏幕图标：系统自己加圆角
  { file: "src/app/apple-icon.png", size: 180, padding: 0.18, radius: 0 },
  { file: "src/app/icon.png", size: 32, padding: 0.12, radius: 0.2 },
];

const browser = await chromium.launch();
const page = await browser.newPage();
for (const { file, size, padding, radius } of outputs) {
  await page.setViewportSize({ width: size, height: size });
  await page.setContent(`<html><body style="margin:0;background:transparent">${svg(size, { padding, radius })}</body></html>`);
  const out = join(root, file);
  mkdirSync(dirname(out), { recursive: true });
  await page.locator("svg").screenshot({ path: out, omitBackground: true });
  console.log("生成", file);
}
await browser.close();
