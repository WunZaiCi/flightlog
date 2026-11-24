import { expect, test } from "@playwright/test";
import { createFlight, signUp } from "./helpers";

test("机型统计页：占比、排行榜、收集墙和详情", async ({ page }) => {
  const errors: string[] = [];
  page.on("console", (msg) => msg.type() === "error" && errors.push(msg.text()));
  page.on("pageerror", (err) => errors.push(err.message));

  await signUp(page);
  await createFlight(page, { flightDate: "2024-05-01", flightNumber: "NH241", depIata: "HND", arrIata: "FUK", aircraftType: "B789", registration: "JA891A" });
  await createFlight(page, { flightDate: "2025-05-01", flightNumber: "NH242", depIata: "FUK", arrIata: "HND", aircraftType: "B789", registration: "JA891A" });
  await createFlight(page, { flightDate: "2025-06-01", flightNumber: "NH21", depIata: "HND", arrIata: "ITM", aircraftType: "B738" });
  await createFlight(page, { flightDate: "2025-07-01", flightNumber: "MU5101", depIata: "PVG", arrIata: "PEK", aircraftType: "X9Z" });

  await page.goto("/aircraft");
  await expect(page.getByRole("heading", { level: 1, name: "机型统计" })).toBeVisible();

  const share = page.getByRole("region", { name: "窄体机和宽体机占比" });
  await expect(share).toContainText("宽体 2 次");
  await expect(share).toContainText("窄体 1 次");

  // ECharts 渲染出了 SVG
  await expect(page.getByRole("img", { name: /机型排行榜/ }).locator("svg")).toBeVisible();
  await expect(page.getByRole("region", { name: "按年份的变化" }).locator("svg")).toBeVisible();

  await expect(page.getByRole("note")).toContainText("X9Z");

  const wall = page.getByRole("region", { name: "机型收集墙" });
  await wall.getByRole("button", { name: /Boeing 787-9/ }).click();
  const dialog = page.getByRole("dialog", { name: "Boeing 787-9" });
  await expect(dialog).toContainText("JA891A");
  await expect(dialog.getByRole("listitem").filter({ hasText: "JA891A" })).toContainText("2 次");
  await dialog.getByRole("button", { name: "关闭" }).click();
  await expect(dialog).toBeHidden();

  expect(errors).toEqual([]);
});
