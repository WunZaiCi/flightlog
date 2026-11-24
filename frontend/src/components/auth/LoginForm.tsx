"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { ApiError } from "@/lib/api";
import { useLogin } from "@/lib/auth";
import { safeNextPath } from "@/lib/safe-next";

export function LoginForm() {
  const router = useRouter();
  const next = safeNextPath(useSearchParams().get("next"));
  const login = useLogin();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const error = login.error instanceof ApiError ? login.error : null;

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    login.mutate({ email, password }, { onSuccess: () => router.replace(next) });
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
        label="密码"
        type="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={error?.fieldErrors.password}
      />
      <Button type="submit" variant="submit" className="w-full" loading={login.isPending}>
        登录
      </Button>
      <p className="text-center text-sm text-muted">
        还没有账号？
        <Link href={next === "/" ? "/register" : `/register?next=${encodeURIComponent(next)}`} className="font-medium text-primary">
          注册
        </Link>
      </p>
    </form>
  );
}
