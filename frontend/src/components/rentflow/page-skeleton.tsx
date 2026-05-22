type PageSkeletonProps = {
  message?: string;
};

export function PageSkeleton({ message = "Đang tải..." }: PageSkeletonProps) {
  return (
    <section
      role="status"
      aria-live="polite"
      className="rounded-xl border border-dashed border-border bg-card p-10 text-center"
    >
      <p className="text-sm text-muted-foreground">{message}</p>
    </section>
  );
}
