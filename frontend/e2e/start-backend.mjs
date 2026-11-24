// Playwright 的 webServer：用临时数据库启动后端 jar，托管 frontend/out（与生产环境的托管方式一致）。
// 前提：已执行 `npm run build` 和 backend 的 `mvnw package`。
// 安全：工作目录设在临时目录，并清空 API key 环境变量，不会读取项目根目录的 .env，也不会碰 data/。
import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, readdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const frontendOut = resolve(here, "../out");
const targetDir = resolve(here, "../../backend/target");
const port = process.env.E2E_PORT ?? "8100";

const jar = existsSync(targetDir)
  ? readdirSync(targetDir).find((f) => /^flightlog-.*\.jar$/.test(f))
  : undefined;
if (!jar) {
  console.error("找不到后端 jar，请先在 backend/ 下执行 mvnw package");
  process.exit(1);
}
if (!existsSync(join(frontendOut, "index.html"))) {
  console.error("找不到 frontend/out，请先执行 npm run build");
  process.exit(1);
}

const workDir = mkdtempSync(join(tmpdir(), "flightlog-e2e-"));
const child = spawn(
  "java",
  [
    "-jar",
    join(targetDir, jar),
    `--server.port=${port}`,
    `--flightlog.data-dir=${join(workDir, "data")}`,
    `--spring.web.resources.static-locations=file:${frontendOut.replaceAll("\\", "/")}/`,
    "--spring.main.banner-mode=off",
  ],
  {
    cwd: workDir,
    stdio: "inherit",
    env: { ...process.env, GEMINI_API_KEY: "", AERODATABOX_API_KEY: "", FLIGHTLOG_DATA_DIR: "" },
  },
);

const cleanup = () => {
  child.kill();
  try {
    rmSync(workDir, { recursive: true, force: true });
  } catch {
    // 数据库文件可能还没释放，留在系统临时目录里也无妨
  }
};
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    cleanup();
    process.exit(0);
  });
}
child.on("exit", (code) => {
  cleanup();
  process.exit(code ?? 0);
});
