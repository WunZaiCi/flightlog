import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LoginForm } from "./LoginForm";
import { RegisterForm } from "./RegisterForm";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: replace }),
  useSearchParams: () => new URLSearchParams("next=/map"),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function problem(status: number, body: object) {
  return new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });
}

describe("登录和注册表单", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    replace.mockReset();
    vi.unstubAllGlobals();
  });

  it("登录成功后跳到 next 指定的页面", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ id: 1, email: "a@example.com", displayName: "A" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    render(<LoginForm />, { wrapper });

    await userEvent.type(screen.getByLabelText("邮箱"), "a@example.com");
    await userEvent.type(screen.getByLabelText("密码"), "correct-horse-1");
    await userEvent.click(screen.getByRole("button", { name: "登录" }));

    expect(replace).toHaveBeenCalledWith("/map");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/auth/login");
    expect(JSON.parse(String(init?.body))).toEqual({ email: "a@example.com", password: "correct-horse-1" });
  });

  it("登录失败显示服务器返回的中文信息", async () => {
    fetchMock.mockResolvedValue(problem(401, { detail: "邮箱或密码错误" }));
    render(<LoginForm />, { wrapper });

    await userEvent.type(screen.getByLabelText("邮箱"), "a@example.com");
    await userEvent.type(screen.getByLabelText("密码"), "wrong-password");
    await userEvent.click(screen.getByRole("button", { name: "登录" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("邮箱或密码错误");
    expect(replace).not.toHaveBeenCalled();
  });

  it("注册时把字段错误显示在对应输入框下", async () => {
    fetchMock.mockResolvedValue(
      problem(400, { detail: "输入内容有误，请检查后重试", errors: { password: "密码长度为 8 到 64 个字符" } }),
    );
    render(<RegisterForm />, { wrapper });

    await userEvent.type(screen.getByLabelText("邮箱"), "a@example.com");
    await userEvent.type(screen.getByLabelText(/^密码/), "longenough");
    await userEvent.type(screen.getByLabelText("确认密码"), "longenough");
    await userEvent.click(screen.getByRole("button", { name: "注册" }));

    const password = screen.getByLabelText(/^密码/);
    expect(await screen.findByText("密码长度为 8 到 64 个字符")).toBeInTheDocument();
    expect(password).toHaveAttribute("aria-invalid", "true");
  });

  it("两次密码不一致时不提交", async () => {
    render(<RegisterForm />, { wrapper });

    await userEvent.type(screen.getByLabelText("邮箱"), "a@example.com");
    await userEvent.type(screen.getByLabelText(/^密码/), "longenough");
    await userEvent.type(screen.getByLabelText("确认密码"), "different1");
    await userEvent.click(screen.getByRole("button", { name: "注册" }));

    expect(await screen.findByText("两次输入的密码不一致")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
