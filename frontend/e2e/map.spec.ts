import { expect, test } from "@playwright/test";
import { createFlight, signUp } from "./helpers";

test("航线地图：在线底图不可用时切换到离线底图，统计和航线详情可用", async ({ page, isMobile }) => {
  // 模拟断网：OpenFreeMap 请求全部失败（测试也不依赖外部服务）
  await page.route("https://tiles.openfreemap.org/**", (route) => route.abort());
  const errors: string[] = [];
  page.on("pageerror", (err) => errors.push(err.message));
  page.on("console", (msg) => {
    // 在线底图被故意拦截，相关的网络错误是预期的
    if (msg.type() === "error" && !msg.text().includes("openfreemap") && !msg.text().includes("Failed to load resource")) errors.push(msg.text());
  });

  await signUp(page);
  await createFlight(page, { flightDate: "2025-03-01", flightNumber: "NH241", depIata: "HND", arrIata: "FUK", aircraftType: "B789" });
  await createFlight(page, { flightDate: "2025-03-05", flightNumber: "NH242", depIata: "FUK", arrIata: "HND", aircraftType: "B789" });
  await createFlight(page, { flightDate: "2026-01-10", flightNumber: "UA837", depIata: "SFO", arrIata: "NRT", aircraftType: "B789" });

  await page.goto("/map");
  const map = page.getByTestId("route-map");
  await expect(map).toHaveAttribute("data-basemap", "offline", { timeout: 15_000 });
  await expect(map.locator("canvas")).toBeVisible();
  await expect(page.getByText("离线底图")).toBeVisible();

  if (isMobile) await page.getByRole("button", { name: "筛选和统计" }).click();
  const summary = page.getByRole("region", { name: "航线统计" });
  await expect(summary).toContainText("国家和地区");
  await expect(summary).toContainText("最长");
  await expect(summary).toContainText("NRT ⇄ SFO");

  await page.getByRole("region", { name: "航线" }).getByRole("button", { name: /FUK ⇄ HND/ }).click();
  const detail = page.getByRole("region", { name: "FUK ⇄ HND" });
  await expect(detail.getByRole("listitem")).toHaveCount(2);

  // 航线和机场确实画到了地图上（worker 正常工作时 GeoJSON 图层才有要素）
  await expect
    .poll(() => page.evaluate(() => document.querySelector("[data-testid=route-map]")?.getAttribute("data-features")))
    .toBe("2");

  // 按年份筛选后只剩一条航线
  await page.getByLabel("年份").selectOption("2026");
  await expect(page.getByRole("region", { name: "航线" }).getByRole("button")).toHaveCount(1);

  expect(errors).toEqual([]);
});

test("航线地图：在线底图加载好后保持在线，筛选会更新地图上的航线", async ({ page, isMobile }) => {
  // 用一个最小的合法样式代替 OpenFreeMap（不依赖外部服务），其他请求一律失败
  await page.route("https://tiles.openfreemap.org/**", (route) =>
    route.request().url().endsWith("/styles/positron")
      ? route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({ version: 8, sources: {}, layers: [{ id: "bg", type: "background", paint: { "background-color": "#eeeeee" } }] }),
        })
      : route.abort(),
  );

  await signUp(page);
  await createFlight(page, { flightDate: "2025-03-01", flightNumber: "NH241", depIata: "HND", arrIata: "FUK" });
  await createFlight(page, { flightDate: "2026-01-10", flightNumber: "UA837", depIata: "SFO", arrIata: "NRT" });

  await page.goto("/map");
  const map = page.getByTestId("route-map");
  await expect.poll(() => map.getAttribute("data-features")).toBe("2");

  // 超过 8 秒的切换时限后仍然是在线底图
  await page.waitForTimeout(9_000);
  await expect(map).toHaveAttribute("data-basemap", "online");

  if (isMobile) {
    const zoomIn = page.getByRole("button", { name: "Zoom in" });
    const box = await zoomIn.boundingBox();
    expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
    await page.getByRole("button", { name: "筛选和统计" }).click();
  }
  await page.getByLabel("年份").selectOption("2026");
  await expect.poll(() => map.getAttribute("data-features")).toBe("1");
});
