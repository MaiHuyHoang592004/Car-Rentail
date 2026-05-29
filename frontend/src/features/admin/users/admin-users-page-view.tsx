"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { FormError } from "@/components/rentflow/form-error";
import { PageHeader } from "@/components/rentflow/page-header";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { adminListUsers } from "@/features/admin/users/api";
import {
  ADMIN_USER_ROLE_FILTERS,
  ADMIN_USER_STATUS_FILTERS,
  type AdminUserFilterRole,
  type AdminUserFilterStatus,
} from "@/features/admin/users/types";

/* ------------------------------------------------------------------ */
/*  Helpers                                                           */
/* ------------------------------------------------------------------ */

function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleDateString("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    });
  } catch {
    return iso;
  }
}

function statusBadge(status: string) {
  if (status === "ACTIVE") return "bg-emerald-100 text-emerald-800 border-emerald-200";
  if (status === "SUSPENDED") return "bg-rose-100 text-rose-800 border-rose-200";
  return "bg-slate-100 text-slate-600 border-slate-200";
}

function verificationBadge(status: string) {
  if (status === "APPROVED") return "bg-emerald-100 text-emerald-800 border-emerald-200";
  if (status === "PENDING") return "bg-amber-100 text-amber-800 border-amber-200";
  if (status === "REJECTED") return "bg-rose-100 text-rose-800 border-rose-200";
  return "bg-slate-100 text-slate-600 border-slate-200";
}

/* ------------------------------------------------------------------ */
/*  Filter chip                                                       */
/* ------------------------------------------------------------------ */

function FilterChip({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors",
        active
          ? "border-primary bg-primary text-primary-foreground"
          : "border-border bg-background text-foreground hover:bg-accent",
      ].join(" ")}
    >
      {label}
    </button>
  );
}

/* ------------------------------------------------------------------ */
/*  Page View                                                         */
/* ------------------------------------------------------------------ */

export function AdminUsersPageView() {
  const [statusFilter, setStatusFilter] = useState<AdminUserFilterStatus>("ALL");
  const [roleFilter, setRoleFilter] = useState<AdminUserFilterRole>("ALL");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const { data, isLoading, isError } = useQuery({
    queryKey: ["admin", "users", statusFilter, roleFilter, page],
    queryFn: () =>
      adminListUsers({ status: statusFilter, role: roleFilter, page, size: pageSize }),
  });

  const users = data?.users ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <AppShell activePath="/admin/users">
      <div className="space-y-6">
        <PageHeader
          title="Quản lý người dùng"
          description="Xem danh sách người dùng với bộ lọc trạng thái và vai trò."
        />

        {/* Filters */}
        <section className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-3">
          <div>
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              Trạng thái
            </p>
            <div className="flex flex-wrap gap-2">
              {ADMIN_USER_STATUS_FILTERS.map((s) => (
                <FilterChip
                  key={s}
                  label={s}
                  active={statusFilter === s}
                  onClick={() => {
                    setStatusFilter(s);
                    setPage(0);
                  }}
                />
              ))}
            </div>
          </div>
          <div>
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              Vai trò
            </p>
            <div className="flex flex-wrap gap-2">
              {ADMIN_USER_ROLE_FILTERS.map((r) => (
                <FilterChip
                  key={r}
                  label={r}
                  active={roleFilter === r}
                  onClick={() => {
                    setRoleFilter(r);
                    setPage(0);
                  }}
                />
              ))}
            </div>
          </div>
        </section>

        {/* Content */}
        {isLoading ? (
          <PageSkeleton message="Đang tải danh sách người dùng..." />
        ) : isError ? (
          <FormError>Không tải được danh sách người dùng. Vui lòng thử lại.</FormError>
        ) : users.length === 0 ? (
          <EmptyState
            title="Không có người dùng"
            description="Không tìm thấy người dùng nào phù hợp với bộ lọc."
          />
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[700px] text-left text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50 text-[11px] uppercase tracking-wide text-muted-foreground">
                  <th className="px-4 py-2.5 font-semibold">Email</th>
                  <th className="px-4 py-2.5 font-semibold">Họ tên</th>
                  <th className="px-4 py-2.5 font-semibold">Vai trò</th>
                  <th className="px-4 py-2.5 font-semibold">Trạng thái</th>
                  <th className="px-4 py-2.5 font-semibold">GPLX</th>
                  <th className="px-4 py-2.5 font-semibold">Tạo lúc</th>
                  <th className="px-4 py-2.5 font-semibold">Đăng nhập cuối</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {users.map((user) => (
                  <tr key={user.id} className="hover:bg-muted/30">
                    <td className="px-4 py-2.5 text-sm font-medium text-foreground">
                      {user.email}
                    </td>
                    <td className="px-4 py-2.5 text-sm text-foreground">{user.fullName}</td>
                    <td className="px-4 py-2.5">
                      <div className="flex flex-wrap gap-1">
                        {user.roles.map((role) => (
                          <span
                            key={role}
                            className="inline-flex items-center rounded-full border border-border bg-background px-2 py-0.5 text-[10px] font-semibold text-foreground"
                          >
                            {role}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-2.5">
                      <span
                        className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold ${statusBadge(user.status)}`}
                      >
                        {user.status}
                      </span>
                    </td>
                    <td className="px-4 py-2.5">
                      <span
                        className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold ${verificationBadge(user.driverVerificationStatus)}`}
                      >
                        {user.driverVerificationStatus}
                      </span>
                    </td>
                    <td className="px-4 py-2.5 text-xs text-muted-foreground">
                      {formatDateTime(user.createdAt)}
                    </td>
                    <td className="px-4 py-2.5 text-xs text-muted-foreground">
                      {formatDateTime(user.lastLoginAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 pt-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:bg-accent"
            >
              ← Trước
            </button>
            <span className="text-xs text-muted-foreground">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:bg-accent"
            >
              Sau →
            </button>
          </div>
        )}
      </div>
    </AppShell>
  );
}