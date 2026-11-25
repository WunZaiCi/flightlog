import { expect, test, type Page } from "@playwright/test";
import { createFlight, signUp } from "./helpers";

/** 页面没有横向滚动，所有可见的按钮、输入框、下拉框高度不小于 44px。 */
async function checkMobileLayout(page: Page, name: string) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow, `${name} 有横向滚动`).toBeLessThanOrEqual(0);

  const small = await page.evaluate(() => {
    const result: string[] = [];
    const targets = document.querySelectorAll<HTMLElement>(
      "button, input:not([type=hidden]):not(.sr-only), select, textarea, [role=tab], summary",
    );
    for (const el of targets) {
      const rect = el.getBoundingClientRect();
      const style = getComputedStyle(el);
      const hidden = rect.width === 0 || rect.height === 0 || style.visibility === "hidden" || el.closest(".maplibregl-ctrl-attrib-inner");
      if (!hidden && rect.height < 44) result.push(`${el.tagName} "${(el.textContent || el.getAttribute("aria-label") || "").trim().slice(0, 20)}" ${Math.round(rect.height)}px`);
    }
    return result;
  });
  expect(small, `${name} 有小于 44px 的点击区域`).toEqual([]);
}

test("所有页面在手机宽度下布局正常、点击区域足够大", async ({ page, isMobile }) => {
  test.skip(!isMobile, "只针对手机视口");
  await page.route("https://tiles.openfreemap.org/**", (route) => route.abort());

  // 未登录页面
  for (const [path, name] of [["/login", "登录"], ["/register", "注册"]]) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await checkMobileLayout(page, name);
  }

  await signUp(page);
  const { id } = await createFlight(page, { flightDate: "2025-03-01", flightNumber: "NH241", depIata: "HND", arrIata: "FUK", aircraftType: "B789", registration: "JA891A" });
  await createFlight(page, { flightDate: "2026-01-10", flightNumber: "UA837", depIata: "SFO", arrIata: "NRT", aircraftType: "X9Z" });

  const pages: [string, string][] = [
    ["/", "总览"],
    ["/flights", "航班"],
    ["/flights/new", "录入航班"],
    [`/flights/edit?id=${id}`, "编辑航班"],
    ["/aircraft", "机型统计"],
    ["/map", "航线地图"],
    ["/settings", "设置"],
  ];
  for (const [path, name] of pages) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1, name })).toBeAttached();
    await page.waitForLoadState("networkidle");
    await checkMobileLayout(page, name);
    await page.screenshot({ path: `test-results/mobile-${name}.png`, fullPage: true });
  }

  // 录入页的另外两个标签、地图的抽屉
  await page.goto("/flights/new");
  for (const tab of ["航班号查询", "AI 解析"]) {
    await page.getByRole("tab", { name: tab }).click();
    await checkMobileLayout(page, `录入-${tab}`);
  }
  await page.goto("/map");
  await page.getByRole("button", { name: "筛选和统计" }).click();
  await checkMobileLayout(page, "地图抽屉");
  await page.screenshot({ path: "test-results/mobile-地图抽屉.png" });
});

test("手机：地图抽屉打开时底部标签栏仍然可以点击", async ({ page, isMobile }) => {
  test.skip(!isMobile, "只针对手机视口");
  await page.route("https://tiles.openfreemap.org/**", (route) => route.abort());
  await signUp(page);
  await page.goto("/map");
  await page.getByRole("button", { name: "筛选和统计" }).click();
  await page.getByRole("navigation", { name: "底部标签栏" }).getByRole("link", { name: "航班" }).click();
  await expect(page).toHaveURL(/\/flights$/);
});
