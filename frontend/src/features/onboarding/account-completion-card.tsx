"use client";

import Link from "next/link";

import { StatusBadge } from "@/components/rentflow/status-badge";
import type { ProfileViewModel } from "@/features/profile/types";
import { customerOnboardingSteps, customerReadyToBook, driverStatusLabel } from "@/features/onboarding/model";

export function AccountCompletionCard({ profile }: { profile: ProfileViewModel }) {
  const isCustomer = profile.roles.includes("CUSTOMER");
  const steps = isCustomer ? customerOnboardingSteps(profile) : [];
  const ready = isCustomer ? customerReadyToBook(profile) : profile.emailVerified;
  const completed = isCustomer
    ? steps.filter((step) => step.state === "complete").length
    : profile.emailVerified
      ? 1
      : 0;
  const total = isCustomer ? steps.length : 1;
  const primaryHref = isCustomer
    ? ready
      ? "/listings"
      : steps.find((step) => step.state !== "complete")?.href ?? "/onboarding/customer"
    : profile.roles.includes("HOST")
      ? "/onboarding/host"
      : "/me/profile";

  return (
    <section className="rounded-3xl border border-slate-200 bg-gradient-to-br from-white via-slate-50 to-blue-50 p-5 shadow-sm">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-blue-700">Trạng thái tài khoản</p>
          <h2 className="mt-2 text-xl font-bold text-slate-950">
            {ready ? "Hồ sơ đã sẵn sàng" : "Hoàn tất hồ sơ để mở khóa thao tác tiếp theo"}
          </h2>
          <p className="mt-2 text-sm text-slate-600">
            {isCustomer
              ? "RentFlow kiểm tra email và GPLX trước khi bạn gửi booking."
              : "Theo dõi xác minh email và các bước vận hành tài khoản."}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={ready ? "APPROVED" : "PENDING"} label={`${completed}/${total} hoàn tất`} />
          <Link
            href={primaryHref}
            className="rounded-full bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-blue-700"
          >
            {ready ? "Tiếp tục" : "Hoàn tất onboarding"}
          </Link>
        </div>
      </div>
      {isCustomer ? (
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <MiniStatus title="Email" value={profile.emailVerified ? "Đã xác minh" : "Chưa xác minh"} done={profile.emailVerified} />
          <MiniStatus
            title="GPLX"
            value={driverStatusLabel(profile.driverVerificationStatus)}
            done={profile.driverVerificationStatus === "APPROVED"}
          />
        </div>
      ) : null}
    </section>
  );
}

function MiniStatus({ title, value, done }: { title: string; value: string; done: boolean }) {
  return (
    <div className="rounded-2xl border border-white/70 bg-white/75 p-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</p>
      <p className="mt-1 text-sm font-semibold text-slate-950">{value}</p>
      <p className={done ? "mt-1 text-xs text-emerald-700" : "mt-1 text-xs text-amber-700"}>
        {done ? "Đã hoàn tất" : "Cần xử lý"}
      </p>
    </div>
  );
}
