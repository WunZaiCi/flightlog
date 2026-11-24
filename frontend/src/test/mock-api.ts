import { vi } from "vitest";

type Handler = (url: URL, init: RequestInit | undefined) => Response | Promise<Response>;

export function json(status: number, body: unknown, contentType = "application/json") {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": contentType },
  });
}

export function problem(status: number, body: Record<string, unknown>) {
  return json(status, { status, ...body }, "application/problem+json");
}

/**
 * 按 "METHOD /path" 路由的 fetch 模拟。没有匹配的请求返回 404，方便发现漏掉的接口。
 * 返回的 mock 记录所有调用，用 calls() 查看。
 */
export function mockApi(routes: Record<string, Handler>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    const method = (init?.method ?? "GET").toUpperCase();
    const handler = routes[`${method} ${url.pathname}`];
    return handler ? handler(url, init) : problem(404, { detail: `未模拟的接口 ${method} ${url.pathname}` });
  });
  vi.stubGlobal("fetch", fetchMock);
  return {
    fetchMock,
    calls: (method: string, path: string) =>
      fetchMock.mock.calls
        .filter(([input, init]) => {
          const url = new URL(String(input), "http://localhost");
          return (init?.method ?? "GET").toUpperCase() === method && url.pathname === path;
        })
        .map(([, init]) => (init?.body ? JSON.parse(String(init.body)) : undefined)),
  };
}
