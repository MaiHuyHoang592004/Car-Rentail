import Link from "next/link";

export function PublicHero() {
  return (
    <section className="relative overflow-hidden rounded-2xl border border-border bg-card">
      <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full bg-primary/15 blur-3xl" />
      <div className="absolute -bottom-24 -left-24 h-72 w-72 rounded-full bg-secondary/20 blur-3xl" />

      <div className="relative grid gap-8 px-6 py-10 md:grid-cols-[1.1fr_0.9fr] md:px-10 md:py-14">
        <div className="space-y-5">
          <p className="inline-flex rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-xs font-bold uppercase tracking-wide text-primary">
            Phase 5 Marketplace
          </p>
          <h1 className="max-w-xl text-4xl font-bold leading-tight text-foreground md:text-5xl">
            Reliable car rental for guests, hosts, and operators.
          </h1>
          <p className="max-w-lg text-base text-muted-foreground">
            RentFlow connects customers to verified listings and gives hosts/admins clean operational
            workflows. This version is static UI with production-structured components.
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <Link
              href="/listings"
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
            >
              Browse vehicles
            </Link>
            <Link
              href="/register"
              className="rounded-full border border-border bg-background px-5 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Create account
            </Link>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <img
            src="https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=700&q=80"
            alt="Premium sedan"
            className="h-44 w-full rounded-xl object-cover sm:h-full"
          />
          <img
            src="https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=700&q=80"
            alt="SUV lineup"
            className="h-44 w-full rounded-xl object-cover sm:h-full"
          />
        </div>
      </div>
    </section>
  );
}
