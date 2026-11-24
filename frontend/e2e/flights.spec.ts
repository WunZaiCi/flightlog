import { expect, test, type Page } from "@playwright/test";
import { createFlight, signUp } from "./helpers";

async function pick(page: Page, label: string, text: string, option: RegExp) {
  const input = page.getByRole("combobox", { name: label });
  await input.fill(text);
  await page.getByRole("listbox", { name: label }).getByRole("option", { name: option }).first().click();
}

async function fillFlight(page: Page, number: string) {
  await page.getByLabel("日期").fill("2026-11-03");
  await page.getByLabel("航班号").fill(number);
  await pick(page, "出发机场", "fuk", /^FUK/);
  await pick(page, "到达机场", "haneda", /^HND/);
  await pick(page, "机型", "787-9", /Boeing 787-9/);
  await page.getByLabel("注册号").fill("ja891a");
}

test("录入、编辑、删除航班，重复录入时提示", async ({ page }) => {
  await signUp(page);
  await page.goto("/flights/new");

  await fillFlight(page, "nh 242");
  await expect(page.getByText("Fukuoka Airport · Fukuoka, JP")).toBeVisible();
  await page.getByRole("button", { name: "保存" }).click();

  await expect(page).toHaveURL(/\/flights$/);
  const card = page.getByRole("listitem").filter({ hasText: "NH242" });
  await expect(card).toContainText("All Nippon Airways");
  await expect(card).toContainText("Boeing 787-9");
  await expect(card).toContainText("JA891A");
  await expect(card).toContainText(/88\d km/);

  // 同一天同一航班号再录一次：提示可能重复，确认后保存
  await page.goto("/flights/new");
  await fillFlight(page, "NH242");
  await page.getByRole("button", { name: "保存" }).click();
  const warning = page.getByRole("main").getByRole("alert");
  await expect(warning).toContainText("可能是重复录入");
  await warning.getByRole("button", { name: "仍然保存" }).click();
  await expect(page).toHaveURL(/\/flights$/);
  await expect(page.getByRole("listitem").filter({ hasText: "NH242" })).toHaveCount(2);

  // 编辑第一条：改成未识别机型
  await page.getByRole("link", { name: "编辑 NH242" }).first().click();
  await expect(page.getByLabel("航班号")).toHaveValue("NH242");
  await page.getByRole("combobox", { name: "机型" }).fill("X9Z");
  await expect(page.getByText(/未识别机型/)).toBeVisible();
  await page.getByRole("button", { name: "保存" }).click();
  await expect(page).toHaveURL(/\/flights$/);
  await expect(page.getByText("未识别机型 X9Z")).toBeVisible();

  // 删除
  await page.getByRole("button", { name: "删除 NH242" }).first().click();
  await page.getByRole("button", { name: "确认删除" }).click();
  await expect(page.getByRole("listitem").filter({ hasText: "NH242" })).toHaveCount(1);
});

test("机场填错时显示字段错误", async ({ page }) => {
  await signUp(page);
  await page.goto("/flights/new");
  await page.getByLabel("航班号").fill("NH1");
  await page.getByRole("combobox", { name: "出发机场" }).fill("ZZZ");
  await page.getByRole("combobox", { name: "到达机场" }).fill("HND");
  await page.getByRole("button", { name: "保存" }).click();
  await expect(page.getByText("找不到机场 ZZZ")).toBeVisible();
});

test("按年份、机场筛选和搜索航班", async ({ page, isMobile }) => {
  await signUp(page);
  await createFlight(page, { flightDate: "2025-03-01", flightNumber: "NH242", depIata: "FUK", arrIata: "HND", aircraftType: "B789", registration: "JA891A" });
  await createFlight(page, { flightDate: "2025-09-10", flightNumber: "JL305", depIata: "HND", arrIata: "KIX", aircraftType: "A359" });
  await createFlight(page, { flightDate: "2026-02-14", flightNumber: "MU5101", depIata: "PVG", arrIata: "PEK", aircraftType: "A333" });

  await page.goto("/flights");
  const flights = page.getByRole("main").getByRole("listitem");
  await expect(flights).toHaveCount(3);

  if (isMobile) await page.getByRole("button", { name: "筛选" }).click();
  await page.getByLabel("年份").selectOption("2025");
  await expect(page).toHaveURL(/year=2025/);
  await expect(flights).toHaveCount(2);

  await page.getByLabel("机场").selectOption("FUK");
  await expect(flights).toHaveCount(1);
  await expect(flights).toContainText("NH242");

  // 刷新后筛选条件还在
  await page.reload();
  await expect(flights).toHaveCount(1);

  await page.getByRole("button", { name: "清除筛选" }).click();
  await expect(flights).toHaveCount(3);

  await page.getByRole("searchbox", { name: "搜索" }).fill("beijing");
  await expect(flights).toHaveCount(1);
  await expect(flights).toContainText("MU5101");
});
