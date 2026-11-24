import { expect, test } from "@playwright/test";
import { signUp } from "./helpers";

test.beforeEach(async ({ page }) => {
  await signUp(page);
});

test.describe("应用外壳", () => {
  test("首页显示标题和导航", async ({ page, isMobile }) => {
    await page.goto("/");
    await expect(page).toHaveTitle("FlightLog");
    await expect(page.getByRole("heading", { level: 1, name: "总览" })).toBeVisible();

    const topNav = page.getByRole("navigation", { name: "主导航" });
    const tabBar = page.getByRole("navigation", { name: "底部标签栏" });
    if (isMobile) {
      await expect(topNav).toBeHidden();
      await expect(tabBar).toBeVisible();
    } else {
      await expect(topNav).toBeVisible();
      await expect(tabBar).toBeHidden();
    }
  });

  test("点击导航切换页面", async ({ page, isMobile }) => {
    await page.goto("/");
    const nav = page.getByRole("navigation", { name: isMobile ? "底部标签栏" : "主导航" });

    await nav.getByRole("link", { name: "航班" }).click();
    await expect(page).toHaveURL(/\/flights$/);
    await expect(page.getByRole("heading", { level: 1, name: "航班" })).toBeVisible();
    await expect(nav.getByRole("link", { name: "航班" })).toHaveAttribute("aria-current", "page");

    await nav.getByRole("link", { name: "地图" }).click();
    await expect(page).toHaveURL(/\/map$/);
    await expect(page.getByRole("heading", { level: 1, name: "航线地图" })).toBeVisible();
  });

  test("直接打开深层链接和刷新都不会 404", async ({ page }) => {
    await page.goto("/flights/new");
    await expect(page.getByRole("heading", { level: 1, name: "录入航班" })).toBeVisible();
    await page.reload();
    await expect(page.getByRole("heading", { level: 1, name: "录入航班" })).toBeVisible();
  });

  test("手机：底部标签点击区域不小于 44px，设置在顶部栏", async ({ page, isMobile }) => {
    test.skip(!isMobile, "只针对手机视口");
    await page.goto("/");
    const tabs = page.getByRole("navigation", { name: "底部标签栏" }).getByRole("link");
    await expect(tabs).toHaveCount(5);
    for (const tab of await tabs.all()) {
      const box = await tab.boundingBox();
      expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
      expect(box?.width ?? 0).toBeGreaterThanOrEqual(44);
    }
    await page.getByRole("link", { name: "设置（手机）" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "设置" })).toBeVisible();
  });

  test("页面没有横向滚动", async ({ page }) => {
    await page.goto("/");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
});

test("浏览各页面时没有失败的请求和控制台错误", async ({ page, isMobile }) => {
  const problems: string[] = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") problems.push(`console: ${msg.text()}`);
  });
  page.on("pageerror", (err) => problems.push(`pageerror: ${err.message}`));
  page.on("response", (res) => {
    if (res.status() >= 400) problems.push(`${res.status()} ${res.url()}`);
  });

  await page.goto("/");
  await page.waitForLoadState("networkidle");
  const nav = page.getByRole("navigation", { name: isMobile ? "底部标签栏" : "主导航" });
  for (const label of ["航班", "录入", "机型", "地图", "总览"]) {
    await nav.getByRole("link", { name: label }).click();
    await page.waitForLoadState("networkidle");
  }

  expect(problems).toEqual([]);
});
