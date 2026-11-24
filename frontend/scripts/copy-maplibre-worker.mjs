// MapLibre v6 在运行时按 import.meta.url 找 worker 文件，打包后这个相对路径不存在，
// 所以把 worker 复制到 public/，在代码里用 setWorkerUrl 指过去。dev 和 build 之前自动执行。
import { copyFileSync, mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const pkgDir = dirname(require.resolve("maplibre-gl/package.json"));
const outDir = join(dirname(fileURLToPath(import.meta.url)), "..", "public", "maplibre");
mkdirSync(outDir, { recursive: true });
copyFileSync(join(pkgDir, "dist", "maplibre-gl-worker.mjs"), join(outDir, "maplibre-gl-worker.mjs"));
