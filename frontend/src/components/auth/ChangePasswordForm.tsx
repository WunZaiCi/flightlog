"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { ApiError } from "@/lib/api";
import { useChangePassword } from "@/lib/auth";

/** 设置页里的修改密码表单。改完后当前设备保持登录，其他设备需要用新密码重新登录。 */
export function ChangePasswordForm() {
  const change = useChangePassword();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [confirmError, setConfirmError] = useState<string>();

  const error = change.error instanceof ApiError ? change.error : change.error ? new ApiError(0, "修改失败，请稍后再试") : null;
  const fieldErrors = error?.fieldErrors ?? {};
  const generalError = error && Object.keys(fieldErrors).length === 0 ? error.message : null;

  /** 用户重新输入时，清掉上一次的成功提示和「不一致」提示。服务器返回的字段错误留到下次提交。 */
  function edited(update: () => void) {
    update();
    setConfirmError(undefined);
    if (change.isSuccess) change.reset();
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (newPassword !== confirm) {
      setConfirmError("两次输入的新密码不一致");
      return;
    }
    setConfirmError(undefined);
    change.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          setCurrentPassword("");
          setNewPassword("");
          setConfirm("");
        },
      },
    );
  }

  return (
    <section aria-labelledby="change-password-title" className="rounded-xl bg-white p-4 ring-1 ring-slate-200">
      <h2 id="change-password-title" className="text-base font-semibold text-navy">
        修改密码
      </h2>
      <form onSubmit={onSubmit} noValidate className="mt-3 space-y-4 md:max-w-md">
        {generalError ? (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {generalError}
          </p>
        ) : null}
        {change.isSuccess ? (
          <p role="status" className="rounded-lg bg-primary/10 px-3 py-2 text-sm text-navy">
            密码已修改，其他设备上的登录已失效
          </p>
        ) : null}
        <Field
          label="当前密码"
          type="password"
          autoComplete="current-password"
          required
          maxLength={64}
          value={currentPassword}
          onChange={(e) => edited(() => setCurrentPassword(e.target.value))}
          error={fieldErrors.currentPassword}
        />
        <Field
          label="新密码"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={64}
          value={newPassword}
          onChange={(e) => edited(() => setNewPassword(e.target.value))}
          error={fieldErrors.newPassword}
          hint="8 到 64 个字符"
        />
        <Field
          label="确认新密码"
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(e) => edited(() => setConfirm(e.target.value))}
          error={confirmError}
        />
        <Button type="submit" variant="submit" className="w-full sm:w-auto" loading={change.isPending}>
          修改密码
        </Button>
      </form>
    </section>
  );
}
