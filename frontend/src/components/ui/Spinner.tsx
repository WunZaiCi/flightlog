export function Spinner({ label = "加载中" }: { label?: string }) {
  return (
    <span role="status" className="inline-flex items-center gap-2 text-sm text-muted">
      <span className="size-5 animate-spin rounded-full border-2 border-primary border-t-transparent" aria-hidden="true" />
      {label}
    </span>
  );
}
