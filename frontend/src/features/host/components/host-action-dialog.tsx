"use client";

import type { ReactNode } from "react";

type HostActionDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  tone?: "default" | "danger";
  onClose: () => void;
  onConfirm: () => void;
  children?: ReactNode;
};

export function HostActionDialog({
  open,
  title,
  description,
  confirmLabel,
  tone = "default",
  onClose,
  onConfirm,
  children,
}: HostActionDialogProps) {
  if (!open) {
    return null;
  }

  const confirmClass =
    tone === "danger"
      ? "rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:opacity-90"
      : "rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90";

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/45 p-4">
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-md rounded-xl border border-border bg-card p-5 shadow-lg"
      >
        <h3 className="text-lg font-bold text-foreground">{title}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>

        {children ? <div className="mt-4">{children}</div> : null}

        <div className="mt-5 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
          >
            Close
          </button>
          <button type="button" onClick={onConfirm} className={confirmClass}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
