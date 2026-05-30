"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { CalendarDays, MapPin, Search } from "lucide-react";

const HERO_IMAGE =
  "https://lh3.googleusercontent.com/aida-public/AB6AXuCiKNyraDW6U-PI3b59GrtgAVbagTi3niJH9lQWdO4B2d2Ix5CJ2arIKjarpMiHWG1op3rxMQIfyYnijraTxpLjgLlrvt3D8u65WDQ_sN94VUSaNCFN7pko0MIiwdJkJ3F54aa0Vhee686MvLPGcL6MbmRbwvAYbiCDranB8HPhaMUMRbRnvPgedJW_t-O5nfpEciL2Nn0lOM99Wbneh3ceXY1CQV2kQwJojIYqcGF-KM42pqlJlJ2uGZ3-nFg2LjZtkOQPRwnWV10";

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
    router.push(params.size ? `/listings?${params.toString()}` : "/listings");
  }

  return (
    <section className="relative left-1/2 min-h-[620px] w-screen -translate-x-1/2 overflow-hidden">
      <img
        src={HERO_IMAGE}
        alt="Xe sedan cao cap dang di qua cau do thi vao gio vang"
        className="absolute inset-0 h-full w-full object-cover"
      />
      <div className="absolute inset-0 bg-gradient-to-r from-slate-950/70 via-slate-950/42 to-slate-950/18" />

      <div className="rf-shell-container relative z-10 flex min-h-[620px] items-center py-20">
        <div className="w-full max-w-4xl text-center md:text-left">
          <h1 className="max-w-3xl text-4xl font-bold leading-tight text-white md:text-6xl">
            Thuê xe tự lái, hành trình tự do và minh bạch
          </h1>
          <p className="mt-5 max-w-2xl text-base leading-7 text-white/86 md:text-lg">
            Kết nối trực tiếp với chủ xe uy tín. Quy trình nhanh chóng, giá rõ ràng,
            hỗ trợ xuyên suốt mỗi chuyến đi.
          </p>

          <form
            onSubmit={handleSearch}
            className="mt-8 grid gap-3 rounded-[1.75rem] bg-white/92 p-3 text-left shadow-[0_28px_80px_-32px_rgba(15,23,42,0.55)] backdrop-blur md:grid-cols-[1.1fr_1fr_1fr_auto] md:items-end"
          >
            <SearchField label="Địa điểm" icon={<MapPin className="h-5 w-5" />}>
              <input
                type="text"
                value={city}
                onChange={(e) => setCity(e.target.value)}
                placeholder="Bạn muốn đi đâu?"
                className="h-11 w-full border-0 bg-transparent px-0 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:ring-0"
              />
            </SearchField>

            <SearchField label="Ngày nhận" icon={<CalendarDays className="h-5 w-5" />}>
              <input
                type="date"
                value={pickupDate}
                onChange={(e) => setPickupDate(e.target.value)}
                className="h-11 w-full border-0 bg-transparent px-0 text-sm text-foreground outline-none focus:ring-0"
              />
            </SearchField>

            <SearchField label="Ngày trả" icon={<CalendarDays className="h-5 w-5" />}>
              <input
                type="date"
                value={returnDate}
                onChange={(e) => setReturnDate(e.target.value)}
                className="h-11 w-full border-0 bg-transparent px-0 text-sm text-foreground outline-none focus:ring-0"
              />
            </SearchField>

            <button
              type="submit"
              className="inline-flex h-13 items-center justify-center gap-2 rounded-xl bg-primary px-6 text-sm font-semibold text-primary-foreground shadow-sm transition-colors hover:bg-[#0053db]"
            >
              <Search className="h-4 w-4" />
              Tìm xe ngay
            </button>
          </form>
        </div>
      </div>
    </section>
  );
}

function SearchField({
  label,
  icon,
  children,
}: {
  label: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <label className="block rounded-xl bg-[#f2f4f6] px-4 py-2">
      <span className="text-xs font-semibold text-muted-foreground">{label}</span>
      <span className="mt-1 flex items-center gap-2 text-muted-foreground">
        <span className="text-muted-foreground">{icon}</span>
        {children}
      </span>
    </label>
  );
}
