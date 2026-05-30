"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Ban, RefreshCw, Users } from "lucide-react";

import { AppShell } from "@/components/rentflow/app-shell";
import { EmptyState } from "@/components/rentflow/empty-state";
import { FormError } from "@/components/rentflow/form-error";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { StatusBadge } from "@/components/rentflow/status-badge";
import { adminListUsers, adminReactivateUser, adminSuspendUser } from "@/features/admin/users/api";
import {
  ADMIN_USER_ROLE_FILTERS,
  ADMIN_USER_STATUS_FILTERS,
  type AdminUserFilterRole,
  type AdminUserFilterStatus,
} from "@/features/admin/users/types";

function roleLabel(role: string): string {
  switch (role) {
    case "CUSTOMER": return "Khach hang";
    case "HOST": return "Chu xe";
    case "ADMIN": return "Quan tri";
    default: return role;
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case "ACTIVE": return "Hoat dong";
    case "SUSPENDED": return "Tam ngung";
    case "DELETED": return "Da xoa";
    default: return status;
  }
}

function verificationLabel(status: string): string {
  switch (status) {
    case "APPROVED": return "Da duyet";
    case "PENDING": return "Cho duyet";
    case "REJECTED": return "Bi tu choi";
    default: return status;
  }
}

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleDateString("vi-VN", {
      day: "2-digit", month: "2-digit", year: "numeric",
    });
  } catch { return iso; }
}

function FilterChip({
  label, active, onClick,
}: { label: string; active: boolean; onClick: () => void }) {
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

export function AdminUsersPageView() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<AdminUserFilterStatus>("ALL");
  const [roleFilter, setRoleFilter] = useState<AdminUserFilterRole>("ALL");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const { data, isLoading, isError } = useQuery({
    queryKey: ["admin", "users", statusFilter, roleFilter, page],
    queryFn: () =>
      adminListUsers({ status: statusFilter, role: roleFilter, page, size: pageSize }),
  });

  const suspendMutation = useMutation({
    mutationFn: adminSuspendUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
  });

  const reactivateMutation = useMutation({
    mutationFn: adminReactivateUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
  });

  const users = data?.users ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <AppShell activePath="/admin/users">
      <div className="space-y-5">
        {/* Header */}
        <div className="flex items-center gap-2">
          <Users className="h-5 w-5 text-muted-foreground" />
          <div>
            <h1 className="text-2xl font-bold text-foreground">Quan ly nguoi dung</h1>
            <p className="text-sm text-muted-foreground">
              Xem danh sach nguoi dung voi bo loc trang thai va vai tro.
            </p>
          </div>
        </div>

        {/* Filters */}
        <section className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-3">
          <div>
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              Trang thai
            </p>
            <div className="flex flex-wrap gap-2">
              {ADMIN_USER_STATUS_FILTERS.map((s) => (
                <FilterChip
                  key={s}
                  label={statusLabel(s)}
                  active={statusFilter === s}
                  onClick={() => { setStatusFilter(s); setPage(0); }}
                />
              ))}
            </div>
          </div>
          <div>
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              Vai tro
            </p>
            <div className="flex flex-wrap gap-2">
              {ADMIN_USER_ROLE_FILTERS.map((r) => (
                <FilterChip
                  key={r}
                  label={roleLabel(r)}
                  active={roleFilter === r}
                  onClick={() => { setRoleFilter(r); setPage(0); }}
                />
              ))}
            </div>
          </div>
        </section>

        {/* Content */}
        {isLoading ? (
          <PageSkeleton message="Dang tai danh sach nguoi dung..." />
        ) : isError ? (
          <FormError>Khong tai duoc danh sach nguoi dung. Vui long thu lai.</FormError>
        ) : users.length === 0 ? (
          <EmptyState
            title="Khong co nguoi dung"
            description="Khong tim thay nguoi dung nao phu hop voi bo loc."
          />
        ) : (
          <>
            {/* Desktop table */}
            <div className="hidden lg:block overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
              <table className="w-full min-w-[800px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border bg-muted/50 text-[11px] uppercase tracking-wide text-muted-foreground">
                    <th className="px-4 py-2.5 font-semibold">Email</th>
                    <th className="px-4 py-2.5 font-semibold">Ho ten</th>
                    <th className="px-4 py-2.5 font-semibold">Vai tro</th>
                    <th className="px-4 py-2.5 font-semibold">Trang thai</th>
                    <th className="px-4 py-2.5 font-semibold">GPLX</th>
                    <th className="px-4 py-2.5 font-semibold">Tao luc</th>
                    <th className="px-4 py-2.5 font-semibold">Dang nhap cuoi</th>
                    <th className="px-4 py-2.5 font-semibold">Thao tac</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {users.map((user) => (
                    <tr key={user.id} className="hover:bg-muted/30 transition-colors">
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
                              {roleLabel(role)}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-4 py-2.5">
                        <StatusBadge
                          status={user.status}
                          label={statusLabel(user.status)}
                        />
                      </td>
                      <td className="px-4 py-2.5">
                        <StatusBadge
                          status={user.driverVerificationStatus}
                          label={verificationLabel(user.driverVerificationStatus)}
                        />
                      </td>
                      <td className="px-4 py-2.5 text-xs text-muted-foreground">
                        {formatDate(user.createdAt)}
                      </td>
                      <td className="px-4 py-2.5 text-xs text-muted-foreground">
                        {formatDate(user.lastLoginAt)}
                      </td>
                      <td className="px-4 py-2.5">
                        <div className="flex gap-2">
                          {user.status === "ACTIVE" ? (
                            <button
                              type="button"
                              onClick={() => suspendMutation.mutate(user.id)}
                              className="inline-flex items-center gap-1 rounded-full border border-border px-2 py-1 text-xs font-semibold"
                            >
                              <Ban className="h-3.5 w-3.5" />
                              Tam ngung
                            </button>
                          ) : null}
                          {user.status === "SUSPENDED" ? (
                            <button
                              type="button"
                              onClick={() => reactivateMutation.mutate(user.id)}
                              className="inline-flex items-center gap-1 rounded-full border border-border px-2 py-1 text-xs font-semibold"
                            >
                              <RefreshCw className="h-3.5 w-3.5" />
                              Kich hoat
                            </button>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile cards */}
            <div className="space-y-3 lg:hidden">
              {users.map((user) => (
                <div key={user.id} className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-2">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="font-semibold text-foreground">{user.fullName}</p>
                      <p className="text-xs text-muted-foreground">{user.email}</p>
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <StatusBadge
                        status={user.status}
                        label={statusLabel(user.status)}
                      />
                      <StatusBadge
                        status={user.driverVerificationStatus}
                        label={verificationLabel(user.driverVerificationStatus)}
                      />
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {user.roles.map((role) => (
                      <span
                        key={role}
                        className="rounded-full border border-border bg-background px-2 py-0.5 text-[10px] font-semibold text-foreground"
                      >
                        {roleLabel(role)}
                      </span>
                    ))}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    Tao: {formatDate(user.createdAt)} &middot; DN cuoi: {formatDate(user.lastLoginAt)}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {user.status === "ACTIVE" ? (
                      <button
                        type="button"
                        onClick={() => suspendMutation.mutate(user.id)}
                        className="inline-flex items-center gap-1 rounded-full border border-border px-3 py-1.5 text-xs font-semibold"
                      >
                        <Ban className="h-3.5 w-3.5" />
                        Tam ngung
                      </button>
                    ) : null}
                    {user.status === "SUSPENDED" ? (
                      <button
                        type="button"
                        onClick={() => reactivateMutation.mutate(user.id)}
                        className="inline-flex items-center gap-1 rounded-full border border-border px-3 py-1.5 text-xs font-semibold"
                      >
                        <RefreshCw className="h-3.5 w-3.5" />
                        Kich hoat
                      </button>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-2">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:bg-accent"
                >
                  Trang truoc
                </button>
                <span className="text-xs text-muted-foreground">
                  Trang {page + 1} / {totalPages} ({totalElements} nguoi dung)
                </span>
                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  className="rounded-full border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:bg-accent"
                >
                  Trang sau
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </AppShell>
  );
}
