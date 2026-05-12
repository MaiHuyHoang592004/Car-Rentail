type MetricTileProps = {
  label: string;
  value: string;
  hint?: string;
};

export function MetricTile({ label, value, hint }: MetricTileProps) {
  return (
    <article className="rounded-lg border border-border bg-card p-4 shadow-sm">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-2 text-2xl font-bold text-foreground">{value}</p>
      {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
    </article>
  );
}
