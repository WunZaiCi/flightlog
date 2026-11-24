import { expect, type Page } from "@playwright/test";

export const PASSWORD = "correct-horse-1";

export function uniqueEmail(prefix = "e2e") {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

/** 通过 API 注册一个新用户并保持登录（与浏览器共享 Cookie）。 */
export async function signUp(page: Page, displayName = "测试用户") {
  const email = uniqueEmail();
  await page.request.get("/api/health");
  const csrf = (await page.context().cookies()).find((c) => c.name === "XSRF-TOKEN")?.value;
  const response = await page.request.post("/api/auth/register", {
    data: { email, password: PASSWORD, displayName },
    headers: { "X-XSRF-TOKEN": csrf ?? "" },
  });
  expect(response.status()).toBe(201);
  return { email, password: PASSWORD, displayName };
}
