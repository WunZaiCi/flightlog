export function ComingSoon({ phase }: { phase: string }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-muted">
      即将推出（{phase}）
    </div>
  );
}
