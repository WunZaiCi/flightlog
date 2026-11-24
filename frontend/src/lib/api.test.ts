import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "./api";

function jsonResponse(status: number, body: unknown, contentType = "application/json") {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": contentType },
  });
}

describe("api", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=token-123; path=/";
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  });

  it("GET 返回 JSON，不带 CSRF 头", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 1 }));

    await expect(api.get<{ id: number }>("/api/x")).resolves.toEqual({ id: 1 });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/x");
    expect(new Headers(init?.headers).has("X-XSRF-TOKEN")).toBe(false);
    expect(init?.credentials).toBe("same-origin");
  });

  it("写请求带上 Cookie 里的 CSRF token 和 JSON 请求体", async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, { ok: true }));

    await api.post("/api/auth/login", { email: "a@b.c" });

    const [, init] = fetchMock.mock.calls[0];
    const headers = new Headers(init?.headers);
    expect(init?.method).toBe("POST");
    expect(headers.get("X-XSRF-TOKEN")).toBe("token-123");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(init?.body).toBe(JSON.stringify({ email: "a@b.c" }));
  });

  it("204 返回 undefined", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(api.post("/api/auth/logout")).resolves.toBeUndefined();
  });

  it("ProblemDetail 错误转成带中文信息和字段错误的 ApiError", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        400,
        { status: 400, title: "请求有误", detail: "输入内容有误，请检查后重试", errors: { email: "邮箱格式不正确" } },
        "application/problem+json",
      ),
    );

    const error = await api.post("/api/auth/register", {}).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      message: "输入内容有误，请检查后重试",
      fieldErrors: { email: "邮箱格式不正确" },
    });
  });

  it("非 JSON 的错误响应也有中文提示", async () => {
    fetchMock.mockResolvedValue(new Response("Bad Gateway", { status: 502 }));
    await expect(api.get("/api/x")).rejects.toMatchObject({ status: 502, message: "服务暂时不可用，请稍后再试" });
  });

  it("网络错误转成中文提示", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(api.get("/api/x")).rejects.toMatchObject({ status: 0, message: "无法连接服务器，请检查网络" });
  });
});
