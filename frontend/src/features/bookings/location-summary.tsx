type LocationSummaryProps = {
  pickupLocation: string;
  returnLocation: string;
};

export function LocationSummary({ pickupLocation, returnLocation }: LocationSummaryProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-lg font-bold text-foreground">Pickup & Return</h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Pickup location</p>
          <p className="mt-1 text-sm font-semibold text-foreground">{pickupLocation || "Not set"}</p>
        </div>
        <div className="rounded-lg border border-border bg-background px-3 py-2">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Return location</p>
          <p className="mt-1 text-sm font-semibold text-foreground">{returnLocation || "Not set"}</p>
        </div>
      </div>
    </section>
  );
}
