"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  CalendarDays,
  CarFront,
  Fuel,
  MapPin,
  MessageCircle,
  Settings2,
  ShieldCheck,
  ThumbsUp,
  UserRoundCheck,
  Users,
} from "lucide-react";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { AvailabilityPreview } from "@/features/listings/availability-preview";
import { ListingBookingCard } from "@/features/listings/listing-booking-card";
import { getListingDetailById } from "@/features/listings/api";
import type { ListingDetailViewModel } from "@/features/listings/types";
import { getFuelTypeLabel, getTransmissionLabel } from "@/lib/display-labels";
import { formatMoney } from "@/lib/formatters";

type ListingDetailPageViewProps = {
  listingId: string;
};

const DETAIL_FALLBACK_IMAGES = [
  "https://lh3.googleusercontent.com/aida-public/AB6AXuDpjv45nV7gVgcEdrt2diTnY4wuJBPoSkH7LEvmwkudI9Kew22y1GU5F-OlHjYg3uCTRJYg0ap6dHgT5xqUyia-nT9RwIOE0U6KGlGD-ZLOj_eg1IgGf485wrIpQqz3-Tk4JqS8FtU2n7Sk8nAj3ODpIbMI-dDBkBT9TaI8YAc8K-vrjDUKTrkp2H7figQ3QdQWVCleT7b4Q6E3Hr2fU3jFi9_wgtgAeMLa8NV4T7nNk29DTv2uzcWlQmA5LK5pDEM6EgT-JKkujX8",
  "https://lh3.googleusercontent.com/aida-public/AB6AXuD0vU8i9GSOvHbUvo10R3-Vz4qgZMZsz-kqsLfjycylu4J9rHJcNE1pyPFWJqMWTE6gS0Gta_jmLX_dX6Pek89mhqyj-YLiaXfKzy3o_TiQbtAYvbZS8n7eBPDFW0gvXoKDFpZOKVt9GHxGSQBZbUR5QjO4mFID3UXBiYsJ9gVrs_lkjkdRb5RuVuw00TbDWkAFm4ENhEd6DPvPYi_1PXc00wvTmAKhiDPjSlWTDHJofirLMhH6u-PUNiUIm82d1NQsc8YnkwvNtjI",
  "https://lh3.googleusercontent.com/aida-public/AB6AXuDMg6DFNFczWB2C1kj5pL8eJIgikyTKbw8u5x1U4iOyfnImL1dQ_MDHQ-TRJK6w3ad-KwqPU0DXYbQ9jrYK05AJZ9LC7wR7XiUSjO6MjOHBevsrsic285XEwq30nbSvZZxfJxn-pQuMcRfGUFjfUx-OA9yCL2AKnMnMqzKH7N6IOJtNQ9Xpk3OA6T6zWIFSHEyjH65rFbFLAfiu_SSqGAfLz_oooNI8t6q-rjmA2126FXup4IMc15Wq3TzuGZXoYkcPCEt92kI3wS4",
  "https://lh3.googleusercontent.com/aida-public/AB6AXuAJrsCIWDoN4clDUs2LylLJn2QrKFEwIj9UT2HmdreSl-oOlCrbP7dm9Dnhk0p_nD3YqQrfDp1G0AbZFausqwCaOL2Bld5PF1OFgaXTSCTJAzaEZzUQU3WHxU5gDJmUXd8VA9HpFWafB3u-6eEFdEfqr51qsrw1iHFOl0vJaT9yPuY32dZR1ALVeOEtlTgSoz4UG4F6iGrvmyWTQLv8GeBw43rldRNLtKZCWyxiEddnf8Zo8X-PNB0nTsVi3q2r5eYcJX2xnBTDo-I",
];

export function ListingDetailPageView({ listingId }: ListingDetailPageViewProps) {
  const { data: listing, isLoading } = useQuery({
    queryKey: ["listings", listingId],
    queryFn: () => getListingDetailById(listingId),
  });

  if (isLoading) {
    return (
      <AppShell activePath="/listings">
        <PageSkeleton message="Đang tải thông tin xe..." />
      </AppShell>
    );
  }

  if (!listing) {
    return (
      <AppShell activePath="/listings">
        <EmptyState
          title="Không tìm thấy xe"
          description="Xe này không tồn tại hoặc không còn khả dụng."
        />
      </AppShell>
    );
  }

  return (
    <AppShell activePath="/listings">
      <div className="pb-20 md:pb-0">
        <HeroGallery listing={listing} />

        <div className="grid grid-cols-1 gap-8 pt-8 lg:grid-cols-12">
          <div className="space-y-8 lg:col-span-8">
            <ListingTitleBlock listing={listing} />
            <SpecsGrid listing={listing} />
            <DescriptionSection listing={listing} />
            <AvailabilityPreview listing={listing} />
            <HostSummary />
          </div>

          <aside className="lg:col-span-4">
            <div className="sticky top-24 space-y-4">
              <ListingBookingCard listing={listing} />
              <TrustSignals />
            </div>
          </aside>
        </div>

        <MobileBookingBar listing={listing} />
      </div>
    </AppShell>
  );
}

function HeroGallery({ listing }: { listing: ListingDetailViewModel }) {
  const apiImages = listing.galleryImageUrls.filter(Boolean);
  const images = (apiImages.length > 0 ? apiImages : DETAIL_FALLBACK_IMAGES).slice(0, 5);

  return (
    <section className="grid h-[320px] grid-cols-1 gap-2 overflow-hidden rounded-xl md:h-[500px] md:grid-cols-4 md:grid-rows-2">
      <GalleryImage className="md:col-span-2 md:row-span-2" src={images[0]} alt={listing.title} />
      {images.slice(1, 5).map((src, index) => (
        <GalleryImage key={`${src}-${index}`} className="hidden md:block" src={src} alt={listing.title} />
      ))}
    </section>
  );
}

function GalleryImage({ src, alt, className }: { src: string; alt: string; className?: string }) {
  return (
    <div className={`group relative cursor-pointer overflow-hidden bg-muted ${className ?? ""}`}>
      <img src={src} alt={alt} className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
      <div className="absolute inset-0 bg-gradient-to-t from-black/35 to-transparent opacity-0 transition-opacity group-hover:opacity-100" />
    </div>
  );
}

function ListingTitleBlock({ listing }: { listing: ListingDetailViewModel }) {
  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded-full bg-emerald-50 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.05em] text-emerald-600">
          Sẵn sàng
        </span>
        <span className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground">
          <MapPin className="h-4 w-4" />
          {listing.address || listing.city}
        </span>
      </div>
      <h1 className="text-3xl font-bold leading-tight text-foreground md:text-4xl">{listing.title}</h1>
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <span className="text-muted-foreground">
          Chưa có dữ liệu đánh giá công khai cho xe này.
        </span>
      </div>
    </section>
  );
}

function SpecsGrid({ listing }: { listing: ListingDetailViewModel }) {
  const specs = [
    { icon: Users, label: "Số ghế", value: `${listing.vehicle.seats} chỗ` },
    { icon: Settings2, label: "Truyền động", value: getTransmissionLabel(listing.vehicle.transmission) },
    { icon: Fuel, label: "Nhiên liệu", value: getFuelTypeLabel(listing.vehicle.fuelType) },
    { icon: CalendarDays, label: "Đời xe", value: String(listing.vehicle.year) },
  ];

  return (
    <section className="grid grid-cols-2 gap-4 rounded-xl border border-border bg-white p-5 shadow-sm sm:grid-cols-4">
      {specs.map((spec) => {
        const Icon = spec.icon;
        return (
          <div key={spec.label} className="flex flex-col items-center gap-1.5 p-3 text-center">
            <Icon className="h-8 w-8 text-primary" strokeWidth={1.7} />
            <span className="text-xs font-medium text-muted-foreground">{spec.label}</span>
            <span className="text-sm font-semibold text-foreground">{spec.value}</span>
          </div>
        );
      })}
    </section>
  );
}

function DescriptionSection({ listing }: { listing: ListingDetailViewModel }) {
  return (
    <section className="space-y-4">
      <h2 className="border-l-4 border-primary pl-4 text-xl font-semibold text-foreground">
        Mô tả chi tiết
      </h2>
      <p className="text-base leading-8 text-muted-foreground">{listing.description}</p>
      {listing.extras.length > 0 ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {listing.extras.map((extra) => (
            <div key={extra.id} className="flex items-center justify-between rounded-lg border border-border bg-white px-4 py-3">
              <span className="text-sm font-medium text-foreground">{extra.name}</span>
              <span className="text-sm font-semibold text-primary">{formatMoney(extra.price, extra.currency)}</span>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function HostSummary() {
  return (
    <section className="flex flex-col items-center gap-5 rounded-xl bg-muted p-5 text-center md:flex-row md:text-left">
      <div className="flex size-20 shrink-0 items-center justify-center rounded-full border-2 border-white bg-[#d5e3fc] text-primary">
        <UserRoundCheck className="h-9 w-9" />
      </div>
      <div className="flex-1">
        <h3 className="text-xl font-semibold text-foreground">Thông tin chủ xe</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          RentFlow sẽ hiển thị thêm hồ sơ chủ xe khi API công khai cung cấp dữ liệu xác thực.
        </p>
        <div className="mt-3 flex flex-wrap justify-center gap-2 md:justify-start">
          <span className="inline-flex items-center gap-1 rounded-full bg-[#d5e3fc] px-3 py-1 text-xs font-semibold text-[#3a485b]">
            <ShieldCheck className="h-4 w-4" />
            Chính chủ
          </span>
          <span className="inline-flex items-center gap-1 rounded-full bg-[#dae2fd] px-3 py-1 text-xs font-semibold text-[#3f465c]">
            <ThumbsUp className="h-4 w-4" />
            Phục vụ tốt
          </span>
        </div>
      </div>
      <Link
        href="/login"
        className="inline-flex items-center gap-2 rounded-lg border border-border bg-white px-4 py-2 text-sm font-semibold text-primary hover:bg-accent"
      >
        <MessageCircle className="h-4 w-4" />
        Nhắn tin cho chủ xe
      </Link>
    </section>
  );
}

function TrustSignals() {
  return (
    <section className="space-y-4 rounded-xl bg-[#e6e8ea] p-5">
      <div className="flex items-center gap-3">
        <ShieldCheck className="h-5 w-5 text-emerald-600" />
        <span className="text-sm font-semibold text-foreground">Hoàn tiền 100% nếu chủ xe hủy</span>
      </div>
      <div className="flex items-center gap-3">
        <UserRoundCheck className="h-5 w-5 text-blue-600" />
        <span className="text-sm font-semibold text-foreground">Xe đã được kiểm tra thông tin trước khi hiển thị</span>
      </div>
    </section>
  );
}

function MobileBookingBar({ listing }: { listing: ListingDetailViewModel }) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background p-4 md:hidden">
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="text-sm text-muted-foreground">
            Từ <span className="font-bold text-primary">{formatMoney(listing.basePricePerDay, listing.currency)}</span>/ngày
          </p>
          <p className="text-xs text-muted-foreground">Chọn ngày để xem tổng</p>
        </div>
        <Link
          href={`/listings/${listing.id}/book`}
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground shadow-sm"
        >
          <CarFront className="h-4 w-4" />
          Đặt ngay
        </Link>
      </div>
    </div>
  );
}
