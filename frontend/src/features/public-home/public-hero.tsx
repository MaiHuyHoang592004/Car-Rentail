"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Search } from "lucide-react";

function todayPlus(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().split("T")[0];
}

export function PublicHero() {
  const router = useRouter();
  const [city, setCity] = useState("");
  const [pickupDate, setPickupDate] = useState(todayPlus(1));
  const [returnDate, setReturnDate] = useState(todayPlus(3));

  function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    const params = new URLSearchParams();
    if (city.trim()) params.set("city", city.trim());
    if (pickupDate) params.set("pickupDate", pickupDate);
    if (returnDate) params.set("returnDate", returnDate);
    const query = params.toString();
    const path = query ? "/listings?" + query : "/listings";
    router.push(path);
  }

  return (
    <section className="relative overflow-hidden rounded-2xl border border-border bg-card">
      <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full bg-primary/15 blur-3xl" />
      <div className="absolute -bottom-24 -left-24 h-72 w-72 rounded-full bg-secondary/20 blur-3xl" />

      <div className="relative px-6 py-10 md:px-10 md:py-14">
        <div className="max-w-2xl space-y-5">
          <h1 className="text-4xl font-bold leading-tight text-foreground md:text-5xl">
            Thue xe tu lai de dang, minh bach va an toan
          </h1>
          <p className="text-base text-muted-foreground">
            Tim kiem va dat xe theo thanh pho, ngay thue va ngan sach cua ban. Tat ca xe duoc xac minh, chu xe dang tin cay, va thanh toan bao mat.
          </p>

          <form onSubmit={handleSearch} className="mt-2">
            <div className="flex flex-col gap-2 rounded-xl border border-border bg-background p-2 sm:flex-row sm:items-end">
              <div className="flex-1 space-y-1">
                <label className="px-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Dia diem
                </label>
                <input
                  type="text"
                  value={city}
                  onChange={(e) => setCity(e.target.value)}
                  placeholder="VD: Ho Chi Minh, Ha Noi"
                  className="h-10 w-full rounded-lg border-0 bg-transparent px-2 text-sm text-foreground placeholder:text-muted-foreground/60 focus:outline-none"
                />
              </div>
              <div className="flex-1 space-y-1 sm:flex-[0.8]">
                <label className="px-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Ngay nhan
                </label>
                <input
                  type="date"
                  value={pickupDate}
                  onChange={(e) => setPickupDate(e.target.value)}
                  className="h-10 w-full rounded-lg border-0 bg-transparent px-2 text-sm text-foreground focus:outline-none"
                />
              </div>
              <div className="flex-1 space-y-1 sm:flex-[0.8]">
                <label className="px-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Ngay tra
                </label>
                <input
                  type="date"
                  value={returnDate}
                  onChange={(e) => setReturnDate(e.target.value)}
                  className="h-10 w-full rounded-lg border-0 bg-transparent px-2 text-sm text-foreground focus:outline-none"
                />
              </div>
              <button
                type="submit"
                className="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-lg bg-primary px-5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 sm:w-auto"
              >
                <Search className="size-4" />
                Tim xe
              </button>
            </div>
          </form>

          <div className="flex flex-wrap gap-3 pt-1">
            <TrustPill text="Xe da xac minh" />
            <TrustPill text="Gia minh bach" />
            <TrustPill text="Thanh toan an toan" />
            <TrustPill text="Ho tro khi co su co" />
          </div>
        </div>
      </div>
    </section>
  );
}

function TrustPill({ text }: { text: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground">
      <svg className="size-3 text-emerald-600" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M10 3L5 8.5L2 5.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {text}
    </span>
  );
}