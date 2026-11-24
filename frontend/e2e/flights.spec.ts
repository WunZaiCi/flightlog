import { expect, test, type Page } from "@playwright/test";
import { signUp } from "./helpers";

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
