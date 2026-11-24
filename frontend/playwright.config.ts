import { defineConfig, devices } from "@playwright/test";

const port = process.env.E2E_PORT ?? "8100";
const baseURL = process.env.E2E_BASE_URL ?? `http://localhost:${port}`;

/**
 * 端到端测试：同时跑桌面和手机（390px）两种视口。
 * 默认用 e2e/start-backend.mjs 启动后端 jar 托管 out/（先 npm run build、mvnw package）。
 * 设置 E2E_BASE_URL 可以改为测试已经在运行的服务。
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  use: {
    baseURL,
    locale: "zh-CN",
    timezoneId: "Asia/Tokyo",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 800 } } },
    {
      name: "mobile",
      use: {
        ...devices["iPhone 13"],
        defaultBrowserType: "chromium",
        viewport: { width: 390, height: 844 },
      },
    },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "node e2e/start-backend.mjs",
        url: `${baseURL}/api/health`,
        timeout: 120_000,
        reuseExistingServer: false,
        stdout: "ignore",
        stderr: "pipe",
      },
});
