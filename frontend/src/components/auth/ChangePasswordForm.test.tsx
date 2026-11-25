import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ChangePasswordForm } from "./ChangePasswordForm";

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

async function fill(current: string, next: string, confirm = next) {
  await userEvent.type(screen.getByLabelText("当前密码"), current);
  await userEvent.type(screen.getByLabelText("新密码"), next);
  await userEvent.type(screen.getByLabelText("确认新密码"), confirm);
  await userEvent.click(screen.getByRole("button", { name: "修改密码" }));
}

describe("修改密码表单", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => vi.stubGlobal("fetch", fetchMock));
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("提交当前密码和新密码，成功后提示并清空输入框", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    render(<ChangePasswordForm />, { wrapper });

    await fill("correct-horse-1", "battery-staple-2");

    expect(await screen.findByRole("status")).toHaveTextContent("密码已修改，其他设备上的登录已失效");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/auth/password");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({
      currentPassword: "correct-horse-1",
      newPassword: "battery-staple-2",
    });
    expect(screen.getByLabelText("当前密码")).toHaveValue("");
    expect(screen.getByLabelText("新密码")).toHaveValue("");

    // 重新输入时成功提示消失
    await userEvent.type(screen.getByLabelText("当前密码"), "x");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("当前密码错误时显示在对应输入框下", async () => {
    fetchMock.mockResolvedValue(
      problem(400, { detail: "当前密码不正确", errors: { currentPassword: "当前密码不正确" } }),
    );
    render(<ChangePasswordForm />, { wrapper });

    await fill("wrong-password", "battery-staple-2");

    expect(await screen.findByText("当前密码不正确")).toBeInTheDocument();
    expect(screen.getByLabelText("当前密码")).toHaveAttribute("aria-invalid", "true");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("锁定等没有字段的错误显示在表单上方", async () => {
    fetchMock.mockResolvedValue(problem(429, { detail: "密码输错次数过多，请 15 分钟后再试" }));
    render(<ChangePasswordForm />, { wrapper });

    await fill("wrong-password", "battery-staple-2");

    expect(await screen.findByRole("alert")).toHaveTextContent("密码输错次数过多，请 15 分钟后再试");
  });

  it("两次新密码不一致时不提交", async () => {
    render(<ChangePasswordForm />, { wrapper });

    await fill("correct-horse-1", "battery-staple-2", "different-3");

    expect(await screen.findByText("两次输入的新密码不一致")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await userEvent.clear(screen.getByLabelText("确认新密码"));
    expect(screen.queryByText("两次输入的新密码不一致")).not.toBeInTheDocument();
  });
});
