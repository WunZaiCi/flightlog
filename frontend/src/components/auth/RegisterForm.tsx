"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { ApiError } from "@/lib/api";
import { useRegister } from "@/lib/auth";
import { safeNextPath } from "@/lib/safe-next";

export function RegisterForm() {
  const router = useRouter();
  const next = safeNextPath(useSearchParams().get("next"));
  const register = useRegister();
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [confirmError, setConfirmError] = useState<string>();

  const error = register.error instanceof ApiError ? register.error : null;

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (password !== confirm) {
      setConfirmError("两次输入的密码不一致");
      return;
    }
    setConfirmError(undefined);
    register.mutate(
      { email, password, displayName: displayName.trim() || undefined },
      { onSuccess: () => router.replace(next) },
    );
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      {error ? (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {error.message}
        </p>
      ) : null}
      <Field
        label="邮箱"
        type="email"
        autoComplete="email"
        inputMode="email"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={error?.fieldErrors.email}
      />
      <Field
        label="昵称（可选）"
        autoComplete="nickname"
        maxLength={50}
        value={displayName}
        onChange={(e) => setDisplayName(e.target.value)}
        error={error?.fieldErrors.displayName}
      />
      <Field
        label="密码"
        type="password"
        autoComplete="new-password"
        required
        minLength={8}
        maxLength={64}
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={error?.fieldErrors.password}
        hint="8 到 64 个字符"
      />
      <Field
        label="确认密码"
        type="password"
        autoComplete="new-password"
        required
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
        error={confirmError}
      />
      <Button type="submit" variant="submit" className="w-full" loading={register.isPending}>
        注册
      </Button>
      <p className="text-center text-sm text-muted">
        已有账号？
        <Link href={next === "/" ? "/login" : `/login?next=${encodeURIComponent(next)}`} className="font-medium text-primary">
          登录
        </Link>
      </p>
    </form>
  );
}
