import { expect, test } from "@playwright/test";
import { createFlight, signUp } from "./helpers";

function daysAgo(days: number) {
  return new Date(Date.now() - days * 86_400_000).toISOString().slice(0, 10);
}

test("新用户看到录入引导", async ({ page }) => {
  await signUp(page);
  await page.goto("/");
  await expect(page.getByText("还没有航班记录")).toBeVisible();
});

test("首页显示统计、最近航班和新解锁机型", async ({ page }) => {
  await signUp(page);
  await createFlight(page, { flightDate: daysAgo(400), flightNumber: "NH241", depIata: "HND", arrIata: "FUK", aircraftType: "B789" });
  await createFlight(page, { flightDate: daysAgo(20), flightNumber: "JL305", depIata: "HND", arrIata: "KIX", aircraftType: "A359" });

  await page.goto("/");
  const stats = page.getByRole("list", { name: "统计" });
  await expect(stats.getByRole("listitem").filter({ hasText: "航班" })).toContainText("2");
  await expect(stats.getByRole("listitem").filter({ hasText: "机场" })).toContainText("3");
  await expect(stats.getByRole("listitem").filter({ hasText: "总里程" })).toContainText("km");

  const recent = page.getByRole("region", { name: "最近的航班" });
  await expect(recent.getByRole("listitem").first()).toContainText("JL305");

  const unlocked = page.getByRole("region", { name: "新解锁的机型" });
  await expect(unlocked).toContainText("Airbus A350-900");
  await expect(unlocked).not.toContainText("Boeing 787-9");
});
