import { MapPin } from "lucide-react";

type LocationSummaryProps = {
  pickupLocation: string;
  returnLocation: string;
};

export function LocationSummary({ pickupLocation, returnLocation }: LocationSummaryProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="flex items-center gap-2 text-lg font-bold text-foreground">
        <MapPin className="h-5 w-5 text-primary" />
        Dia diem nhan / tra xe
      </h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="rounded-lg border border-border bg-background px-4 py-3">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Dia diem nhan xe
          </p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {pickupLocation || "Chua dat"}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-background px-4 py-3">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Dia diem tra xe
          </p>
          <p className="mt-1 text-sm font-semibold text-foreground">
            {returnLocation || "Chua dat"}
          </p>
        </div>
      </div>
    </section>
  );
}
