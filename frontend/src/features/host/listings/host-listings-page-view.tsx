"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { toast } from "sonner";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { EmptyState } from "@/components/rentflow/empty-state";
import { FormError } from "@/components/rentflow/form-error";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { HostListingRow } from "@/features/host/components/host-listing-row";
import {
  getHostListings,
  resumeListingSafe,
  type HostListingFilterValue,
  type ListingTransitionError,
} from "@/features/host/listings/api";

const FILTERS: { value: HostListingFilterValue; label: string }[] = [
  { value: "ALL", label: "Tat ca" },
  { value: "DRAFT", label: "Nhap" },
  { value: "PENDING_APPROVAL", label: "Cho duyet" },
  { value: "ACTIVE", label: "Hoat dong" },
  { value: "SUSPENDED", label: "Tam ngung" },
  { value: "ARCHIVED", label: "Luu kho" },
];

export function HostListingsPageView() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<HostListingFilterValue>("ALL");

  const { data, isLoading, isError } = useQuery({
    queryKey: ["host", "listings", statusFilter],
    queryFn: () => getHostListings(statusFilter),
  });

  const listings = data?.listings ?? [];

  const resumeMutation = useMutation({
    mutationFn: (listingId: string) => resumeListingSafe(listingId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["host", "listings"] });
    },
    onError: (error: ListingTransitionError) => {
      toast.error(error.message || "Khong the kich hoat lai tin dang.");
    },
  });

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/listings">
      <div className="space-y-5">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Tin dang cua toi</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Quan ly tin dang xe theo trang thai.
            </p>
          </div>
          <Link
            href="/host/listings/new"
            className="flex items-center gap-1.5 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            <Plus className="h-4 w-4" />
            Tao tin dang
          </Link>
        </div>

        {/* Filter chips */}
        <div className="flex flex-wrap gap-2">
          {FILTERS.map((filter) => {
            const active = statusFilter === filter.value;
            return (
              <button
                key={filter.value}
                type="button"
                onClick={() => setStatusFilter(filter.value)}
                className={[
                  "rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors",
                  active
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border bg-background text-foreground hover:bg-accent",
                ].join(" ")}
              >
                {filter.label}
              </button>
            );
          })}
        </div>

        {/* Listing list */}
        {isLoading ? (
          <PageSkeleton message="Dang tai tin dang..." />
        ) : isError ? (
          <FormError>Khong tai duoc danh sach tin dang. Vui long thu lai.</FormError>
        ) : listings.length === 0 ? (
          <EmptyState
            title="Chua co tin dang nao"
            description="Doi bo loc hoac tao tin dang moi de tiep tuc."
          />
        ) : (
          <div className="space-y-3">
            {listings.map((listing) => (
              <HostListingRow
                key={listing.id}
                listing={listing}
                onResume={listing.status === "SUSPENDED" ? (id) => resumeMutation.mutate(id) : undefined}
                resumePending={resumeMutation.isPending}
              />
            ))}
          </div>
        )}
      </div>
    </WorkspaceSidebar>
  );
}
