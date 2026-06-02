"use client";

import Link from "next/link";
import { CheckCircle2, Clock3, Lock, ShieldCheck } from "lucide-react";
import type { ReactNode } from "react";

import { StatusBadge } from "@/components/rentflow/status-badge";
import type { OnboardingStep } from "@/features/onboarding/model";
import { cn } from "@/lib/utils";

export function OnboardingHero({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-3xl border border-slate-200 bg-gradient-to-br from-slate-50 via-white to-blue-50 p-6 shadow-sm md:p-8">
      <div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
        <div className="max-w-2xl">
          <p className="text-xs font-bold uppercase tracking-[0.22em] text-blue-700">{eyebrow}</p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-950 md:text-4xl">{title}</h1>
          <p className="mt-3 max-w-xl text-sm leading-6 text-slate-600">{description}</p>
        </div>
        {children ? <div className="shrink-0">{children}</div> : null}
      </div>
    </section>
  );
}

export function ChecklistCard({ steps }: { steps: OnboardingStep[] }) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-slate-950">Việc cần hoàn tất</h2>
          <p className="mt-1 text-sm text-slate-600">Theo dõi trạng thái tài khoản trước khi bắt đầu.</p>
        </div>
        <StatusBadge status="ACTIVE" label={`${steps.filter((s) => s.state === "complete").length}/${steps.length}`} />
      </div>
      <div className="mt-5 space-y-3">
        {steps.map((step) => (
          <StepRow key={step.id} step={step} />
        ))}
      </div>
    </section>
  );
}

export function StepRow({ step }: { step: OnboardingStep }) {
  const disabled = step.state === "blocked";
  return (
    <div
      className={cn(
        "flex flex-col gap-3 rounded-2xl border p-4 sm:flex-row sm:items-center sm:justify-between",
        step.state === "complete" && "border-emerald-200 bg-emerald-50/70",
        step.state === "current" && "border-blue-200 bg-blue-50/70",
        step.state === "pending" && "border-amber-200 bg-amber-50/70",
        step.state === "blocked" && "border-slate-200 bg-slate-50",
      )}
    >
      <div className="flex gap-3">
        <StepIcon state={step.state} />
        <div>
          <p className="font-semibold text-slate-950">{step.title}</p>
          <p className="mt-1 text-sm leading-5 text-slate-600">{step.description}</p>
        </div>
      </div>
      {disabled ? (
        <span className="inline-flex rounded-full border border-slate-200 px-4 py-2 text-center text-sm font-semibold text-slate-500">
          Chưa mở
        </span>
      ) : (
        <Link
          href={step.href}
          className="inline-flex justify-center rounded-full bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-blue-700"
        >
          {step.ctaLabel}
        </Link>
      )}
    </div>
  );
}

export function StatusPanel({
  title,
  description,
  status,
  statusLabel,
  actionHref,
  actionLabel,
}: {
  title: string;
  description: string;
  status: string;
  statusLabel: string;
  actionHref?: string;
  actionLabel?: string;
}) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="mb-3">
            <StatusBadge status={status} label={statusLabel} />
          </div>
          <h2 className="text-xl font-bold text-slate-950">{title}</h2>
          <p className="mt-2 text-sm leading-6 text-slate-600">{description}</p>
        </div>
        {actionHref && actionLabel ? (
          <Link
            href={actionHref}
            className="inline-flex justify-center rounded-full bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-700"
          >
            {actionLabel}
          </Link>
        ) : null}
      </div>
    </section>
  );
}

export function FriendlyError({ message }: { message: string }) {
  return (
    <section role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900">
      <p className="font-semibold">Không thể hoàn tất thao tác</p>
      <p className="mt-1">{message}</p>
    </section>
  );
}

function StepIcon({ state }: { state: OnboardingStep["state"] }) {
  const className = "mt-0.5 size-5 shrink-0";
  switch (state) {
    case "complete":
      return <CheckCircle2 className={cn(className, "text-emerald-600")} />;
    case "pending":
      return <Clock3 className={cn(className, "text-amber-600")} />;
    case "blocked":
      return <Lock className={cn(className, "text-slate-400")} />;
    case "current":
      return <ShieldCheck className={cn(className, "text-blue-600")} />;
  }
}
