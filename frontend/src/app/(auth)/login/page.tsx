import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginForm } from "@/components/auth/LoginForm";

export const metadata: Metadata = { title: "登录" };

export default function LoginPage() {
  return (
    <>
      <h1 className="mb-6 text-xl font-bold text-navy">登录</h1>
      <Suspense>
        <LoginForm />
      </Suspense>
    </>
  );
}
