import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthGate } from "./AuthGate";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/flights",
  useSearchParams: () => new URLSearchParams("year=2026"),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("AuthGate", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => vi.stubGlobal("fetch", fetchMock));
  afterEach(() => {
    fetchMock.mockReset();
    replace.mockReset();
    vi.unstubAllGlobals();
  });

  it("未登录时跳到登录页，并记住原来的地址", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ status: 401, detail: "请先登录" }), {
        status: 401,
        headers: { "Content-Type": "application/problem+json" },
      }),
    );
    render(<AuthGate>受保护的内容</AuthGate>, { wrapper });

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fflights%3Fyear%3D2026"));
    expect(screen.queryByText("受保护的内容")).not.toBeInTheDocument();
  });

  it("已登录时显示内容", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ id: 1, email: "a@example.com", displayName: "A" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    render(<AuthGate>受保护的内容</AuthGate>, { wrapper });

    expect(await screen.findByText("受保护的内容")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("连不上服务器时提示并可以重试", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
    render(<AuthGate>受保护的内容</AuthGate>, { wrapper });

    expect(await screen.findByRole("alert")).toHaveTextContent("无法连接服务器，请检查网络");
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
