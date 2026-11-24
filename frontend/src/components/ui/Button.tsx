import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "submit" | "ghost" | "danger";

const VARIANTS: Record<Variant, string> = {
  // 主蓝色：主操作
  primary: "bg-primary text-white hover:bg-primary/90",
  // 紫色：提交和保存
  submit: "bg-secondary text-white hover:bg-secondary/90",
  ghost: "bg-transparent text-navy hover:bg-slate-100",
  danger: "bg-white text-red-600 ring-1 ring-red-200 hover:bg-red-50",
};

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  loading?: boolean;
};

export function Button({ variant = "primary", loading, disabled, className = "", children, ...rest }: ButtonProps) {
  return (
    <button
      disabled={disabled || loading}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-60 ${VARIANTS[variant]} ${className}`}
      {...rest}
    >
      {loading ? <span className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" /> : null}
      {children}
    </button>
  );
}
