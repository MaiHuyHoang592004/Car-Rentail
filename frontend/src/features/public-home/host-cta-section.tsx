import Link from "next/link";

export function HostCtaSection() {
  return (
    <section className="rounded-xl border border-border bg-card p-6">
      <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">
        <div className="flex items-start gap-4">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-secondary/10">
            <svg className="size-5 text-secondary" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" xmlns="http://www.w3.org/2000/svg">
              <path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.5-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2" />
              <circle cx="7" cy="17" r="2" />
              <path d="M9 17h6" />
              <circle cx="17" cy="17" r="2" />
            </svg>
          </div>
          <div>
            <h2 className="font-heading text-lg font-bold text-foreground">Co xe nhan rong?</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Bien xe cua ban thanh nguon thu nhap. Dang ky lam Host va bat dau ngay hom nay.
            </p>
          </div>
        </div>
        <Link
          href="/register"
          className="shrink-0 rounded-full bg-secondary px-5 py-2.5 text-sm font-semibold text-secondary-foreground transition-opacity hover:opacity-90"
        >
          Bat dau cho thue
        </Link>
      </div>
    </section>
  );
}