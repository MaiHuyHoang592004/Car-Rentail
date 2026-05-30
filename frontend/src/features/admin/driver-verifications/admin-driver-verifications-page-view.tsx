"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AppShell } from "@/components/rentflow/app-shell";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import {
  adminApproveDriverVerification,
  adminListDriverVerifications,
  adminRejectDriverVerification,
} from "@/features/admin/driver-verifications/api";

export function AdminDriverVerificationsPageView() {
  const queryClient = useQueryClient();
  const [rejectReason, setRejectReason] = useState("");
  const query = useQuery({
    queryKey: ["admin", "driver-verifications"],
    queryFn: () => adminListDriverVerifications("ALL"),
  });
  const approveMutation = useMutation({
    mutationFn: (id: string) => adminApproveDriverVerification(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "driver-verifications"] }),
  });
  const rejectMutation = useMutation({
    mutationFn: (id: string) => adminRejectDriverVerification(id, rejectReason || "Rejected"),
    onSuccess: () => {
      setRejectReason("");
      queryClient.invalidateQueries({ queryKey: ["admin", "driver-verifications"] });
    },
  });

  return (
    <AppShell activePath="/admin/driver-verifications">
      <div className="space-y-5">
        <h1 className="text-2xl font-bold text-foreground">Duyet GPLX</h1>
        {query.isLoading ? <PageSkeleton message="Dang tai ho so GPLX..." /> : null}
        <div className="grid gap-4 lg:grid-cols-2">
          {(query.data ?? []).map((item) => (
            <section key={item.id} className="rounded-xl border border-border bg-card p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold text-foreground">{item.customerId}</p>
                  <p className="text-sm text-muted-foreground">Het han: {item.licenseExpiryDate}</p>
                  <p className="text-sm text-muted-foreground">
                    Cho duyet: {item.pendingAgeHours ?? 0} gio {item.slaBreached ? "(qua SLA)" : ""}
                  </p>
                </div>
                <span className="rounded-full border border-border px-2 py-1 text-xs font-semibold">{item.status}</span>
              </div>
              {item.documentPreviewUrl ? (
                <a
                  href={item.documentPreviewUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-3 inline-flex rounded-full border border-border px-3 py-1.5 text-xs font-semibold"
                >
                  Xem tai lieu
                </a>
              ) : null}
              {item.status === "PENDING" ? (
                <div className="mt-4 space-y-2">
                  <textarea
                    value={rejectReason}
                    onChange={(event) => setRejectReason(event.target.value)}
                    rows={2}
                    className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
                    placeholder="Ly do tu choi"
                  />
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => approveMutation.mutate(item.id)}
                      className="rounded-full bg-emerald-600 px-4 py-2 text-sm font-semibold text-white"
                    >
                      Duyet
                    </button>
                    <button
                      type="button"
                      onClick={() => rejectMutation.mutate(item.id)}
                      className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white"
                    >
                      Tu choi
                    </button>
                  </div>
                </div>
              ) : null}
            </section>
          ))}
        </div>
      </div>
    </AppShell>
  );
}
