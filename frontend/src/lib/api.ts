/**
 * 调用后端 /api 的封装：同源 Cookie 会话；写请求自动带 CSRF token（从 XSRF-TOKEN Cookie 读取）；
 * 错误统一转成 ApiError，message 是可以直接显示的中文。
 */

export type FieldErrors = Record<string, string>;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: FieldErrors = {},
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  for (const part of document.cookie.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) return decodeURIComponent(rest.join("="));
  }
  return undefined;
}

function fallbackMessage(status: number): string {
  if (status === 401) return "请先登录";
  if (status === 403) return "没有权限执行此操作";
  if (status === 404) return "请求的内容不存在";
  if (status === 429) return "请求太频繁，请稍后再试";
  if (status >= 500) return "服务暂时不可用，请稍后再试";
  return "请求失败，请稍后再试";
}

async function toApiError(response: Response): Promise<ApiError> {
  const type = response.headers.get("Content-Type") ?? "";
  if (type.includes("json")) {
    try {
      const body = (await response.json()) as { detail?: string; errors?: FieldErrors };
      return new ApiError(response.status, body.detail || fallbackMessage(response.status), body.errors ?? {});
    } catch {
      // 响应体不是合法 JSON，用默认提示
    }
  }
  return new ApiError(response.status, fallbackMessage(response.status));
}

type Method = "GET" | "POST" | "PUT" | "DELETE";

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers = new Headers({ Accept: "application/json" });
  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers.set("Content-Type", "application/json");
    payload = JSON.stringify(body);
  }
  if (method !== "GET") {
    const token = readCookie(CSRF_COOKIE);
    if (token) headers.set(CSRF_HEADER, token);
  }

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: payload, credentials: "same-origin" });
  } catch {
    throw new ApiError(0, "无法连接服务器，请检查网络");
  }

  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  const type = response.headers.get("Content-Type") ?? "";
  return (type.includes("json") ? await response.json() : await response.text()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
