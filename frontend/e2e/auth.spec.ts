import { expect, test } from "@playwright/test";
import { PASSWORD, signUp, uniqueEmail } from "./helpers";

test("未登录访问页面会跳到登录页，注册后回到原来的页面", async ({ page, isMobile }) => {
  await page.goto("/map");
  await expect(page).toHaveURL(/\/login\?next=%2Fmap$/);
  await expect(page.getByRole("heading", { name: "登录" })).toBeVisible();

  await page.getByRole("link", { name: "注册" }).click();
  await expect(page).toHaveURL(/\/register\?next=%2Fmap$/);

  const email = uniqueEmail("signup");
  await page.getByLabel("邮箱").fill(email);
  await page.getByLabel("昵称（可选）").fill("小飞");
  await page.getByLabel("密码", { exact: true }).fill(PASSWORD);
  await page.getByLabel("确认密码").fill(PASSWORD);
  await page.getByRole("button", { name: "注册" }).click();

  await expect(page).toHaveURL(/\/map$/);
  await expect(page.getByRole("heading", { level: 1, name: "航线地图" })).toBeVisible();

  if (isMobile) {
    await page.getByRole("link", { name: "设置（手机）" }).click();
    await expect(page.getByText(email)).toBeVisible();
  } else {
    await expect(page.getByRole("banner").getByText("小飞")).toBeVisible();
  }
});

test("刷新页面后仍然是登录状态", async ({ page }) => {
  await signUp(page);
  await page.goto("/flights");
  await expect(page.getByRole("heading", { level: 1, name: "航班" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { level: 1, name: "航班" })).toBeVisible();
  await expect(page).toHaveURL(/\/flights$/);
});

test("退出后回到登录页，再访问页面需要重新登录", async ({ page }) => {
  await signUp(page);
  await page.goto("/settings");
  await page.getByRole("main").getByRole("button", { name: "退出登录" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await page.goto("/");
  await expect(page).toHaveURL(/\/login\?next=%2F$/);
});

test("密码错误时提示，正确后进入首页", async ({ page }) => {
  const user = await signUp(page);
  await page.context().clearCookies();

  await page.goto("/login");
  await page.getByLabel("邮箱").fill(user.email);
  await page.getByLabel("密码").fill("wrong-password");
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toHaveText("邮箱或密码错误");

  await page.getByLabel("密码").fill(user.password);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("heading", { level: 1, name: "总览" })).toBeVisible();
});
