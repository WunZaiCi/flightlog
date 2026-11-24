import type { Metadata } from "next";
import { Suspense } from "react";
import { RegisterForm } from "@/components/auth/RegisterForm";

export const metadata: Metadata = { title: "注册" };

export default function RegisterPage() {
  return (
    <>
      <h1 className="mb-6 text-xl font-bold text-navy">注册</h1>
      <Suspense>
        <RegisterForm />
      </Suspense>
    </>
  );
}
