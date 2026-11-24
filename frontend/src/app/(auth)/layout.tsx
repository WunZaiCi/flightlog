import { Icon } from "@/components/Icon";

/** 登录和注册页：不需要登录，不显示导航。 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col items-center bg-slate-50 px-4 pt-[calc(3rem+env(safe-area-inset-top))] pb-10">
      <div className="mb-8 flex items-center gap-2 text-2xl font-bold tracking-tight text-navy">
        <Icon name="plane" className="size-7 text-accent" />
        FlightLog
      </div>
      <main className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">{children}</main>
    </div>
  );
}
