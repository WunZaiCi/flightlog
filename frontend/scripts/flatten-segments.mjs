// 修正 Next.js 静态导出在 Windows 上的分段预取文件路径。
//
// Next 导出时用 path.relative 得到分段路径，在 Windows 上是反斜杠，而它只把 "/" 换成 "."，
// 结果写成了嵌套目录 out/flights/__next.<group>/flights/__PAGE__.txt；
// 客户端请求的却是扁平文件名 out/flights/__next.<group>.flights.__PAGE__.txt，于是预取全部 404。
// Linux（Docker 构建）下本来就是扁平的，这个脚本什么也不做。
import { readdirSync, renameSync, rmSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

/** 把 outDir 下所有名为 `__next.*` 的目录展开成扁平文件，返回移动的文件数。 */
export function flattenSegments(outDir) {
  let moved = 0;
  for (const entry of readdirSync(outDir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const full = join(outDir, entry.name);
    if (entry.name.startsWith("__next.")) {
      for (const file of listFiles(full)) {
        const flatName = `${entry.name}.${relative(full, file).split(sep).join(".")}`;
        renameSync(file, join(outDir, flatName));
        moved++;
      }
      rmSync(full, { recursive: true, force: true });
    } else {
      moved += flattenSegments(full);
    }
  }
  return moved;
}

function listFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name);
    return statSync(full).isDirectory() ? listFiles(full) : [full];
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const outDir = process.argv[2] ?? "out";
  const moved = flattenSegments(outDir);
  if (moved > 0) {
    console.log(`flatten-segments: 修正了 ${moved} 个分段预取文件的路径`);
  }
}
