"use client";

import type { ReactNode } from "react";
import { useState } from "react";

type HostActionDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  tone?: "default" | "danger";
  onClose: () => void;
  onConfirm: (reason?: string) => void;
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
  const [reason, setReason] = useState("");

  if (!open) {
    return null;
  }

  const confirmClass =
    tone === "danger"
      ? "rounded-lg bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50"
      : "rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50";

  function handleConfirm() {
    onConfirm(reason || undefined);
    setReason("");
  }

  function handleClose() {
    setReason("");
    onClose();
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/45 p-4">
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-md rounded-xl border border-border bg-card p-5 shadow-lg"
      >
        <h3 className="text-lg font-bold text-foreground">{title}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>

        {children}

        <div className="mt-4 flex flex-col gap-3">
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            placeholder="Nhap ly do (tuy chon)"
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          />
          <div className="flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={handleClose}
              className="rounded-lg border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              Huy
            </button>
            <button type="button" onClick={handleConfirm} className={confirmClass}>
              {confirmLabel}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}