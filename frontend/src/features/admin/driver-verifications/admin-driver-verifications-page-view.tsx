"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { StatusBadge } from "@/components/rentflow/status-badge";
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
      <div className="space-y-6">
        <section className="rounded-3xl border border-slate-200 bg-gradient-to-br from-slate-50 via-white to-blue-50 p-6 shadow-sm">
          <p className="text-xs font-bold uppercase tracking-[0.22em] text-blue-700">Admin RentFlow</p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-950">Duyệt giấy phép lái xe</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
            Kiểm tra hồ sơ GPLX của khách thuê, duyệt hồ sơ hợp lệ hoặc từ chối kèm lý do rõ ràng.
          </p>
        </section>

        {query.isLoading ? <PageSkeleton message="Dang tai ho so GPLX..." /> : null}
        {query.isError ? (
          <section className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900">
            Không thể tải danh sách GPLX. Vui lòng thử lại.
          </section>
        ) : null}
        {!query.isLoading && !query.isError && (query.data ?? []).length === 0 ? (
          <EmptyState title="Không có hồ sơ GPLX cần xử lý" description="Khi khách thuê gửi GPLX, hồ sơ sẽ xuất hiện tại đây." />
        ) : null}
        <div className="grid gap-4 lg:grid-cols-2">
          {(query.data ?? []).map((item) => (
            <section key={item.id} className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Khách thuê</p>
                  <p className="mt-1 font-semibold text-slate-950">{item.customerId}</p>
                  <p className="mt-2 text-sm text-slate-600">Hết hạn GPLX: {item.licenseExpiryDate}</p>
                  <p className="text-sm text-slate-600">
                    Chờ duyệt: {item.pendingAgeHours ?? 0} giờ {item.slaBreached ? "(quá SLA)" : ""}
                  </p>
                </div>
                <StatusBadge status={item.status} />
              </div>
              {item.documentPreviewUrl ? (
                <a
                  href={item.documentPreviewUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-4 inline-flex rounded-full border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-800 transition-colors hover:bg-slate-50"
                >
                  Xem tài liệu
                </a>
              ) : null}
              {item.status === "PENDING" ? (
                <div className="mt-4 space-y-2">
                  <textarea
                    value={rejectReason}
                    onChange={(event) => setRejectReason(event.target.value)}
                    rows={2}
                    className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm outline-none ring-blue-200 focus:ring-4"
                    placeholder="Lý do từ chối"
                  />
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => approveMutation.mutate(item.id)}
                      disabled={approveMutation.isPending}
                      className="rounded-full bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-60"
                    >
                      Duyệt
                    </button>
                    <button
                      type="button"
                      onClick={() => rejectMutation.mutate(item.id)}
                      disabled={rejectMutation.isPending}
                      className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-60"
                    >
                      Từ chối
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
