"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AppShell } from "@/components/rentflow/app-shell";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { adminGetDispute, adminListDisputes, adminResolveDispute } from "@/features/admin/disputes/api";

export function AdminDisputesPageView() {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [resolutionNote, setResolutionNote] = useState("");

  const listQuery = useQuery({
    queryKey: ["admin", "disputes"],
    queryFn: () => adminListDisputes("ALL"),
  });
  const detailQuery = useQuery({
    queryKey: ["admin", "disputes", selectedId],
    queryFn: () => adminGetDispute(selectedId!),
    enabled: Boolean(selectedId),
  });
  const resolveMutation = useMutation({
    mutationFn: () => adminResolveDispute(selectedId!, { resolutionNote }),
    onSuccess: () => {
      setResolutionNote("");
      queryClient.invalidateQueries({ queryKey: ["admin", "disputes"] });
    },
  });

  return (
    <AppShell activePath="/admin/disputes">
      <div className="grid gap-5 lg:grid-cols-[360px_minmax(0,1fr)]">
        <section>
          <h1 className="text-2xl font-bold text-foreground">Khieu nai</h1>
          {listQuery.isLoading ? <PageSkeleton message="Dang tai khieu nai..." /> : null}
          <div className="mt-4 space-y-2">
            {(listQuery.data ?? []).map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setSelectedId(item.id)}
                className="w-full rounded-lg border border-border bg-card p-3 text-left text-sm hover:bg-accent"
              >
                <p className="font-semibold text-foreground">{item.category}</p>
                <p className="mt-1 line-clamp-2 text-muted-foreground">{item.reason}</p>
                <p className="mt-2 text-xs text-muted-foreground">{item.status}</p>
              </button>
            ))}
          </div>
        </section>

        <section className="rounded-xl border border-border bg-card p-5">
          {!selectedId ? <p className="text-sm text-muted-foreground">Chon mot khieu nai de xem chi tiet.</p> : null}
          {detailQuery.data ? (
            <div className="space-y-4">
              <div>
                <p className="text-xs uppercase text-muted-foreground">Booking</p>
                <p className="font-semibold text-foreground">{detailQuery.data.booking.id}</p>
                <p className="text-sm text-muted-foreground">{detailQuery.data.booking.status}</p>
              </div>
              <div>
                <p className="text-xs uppercase text-muted-foreground">Thanh toan</p>
                <p className="text-sm text-foreground">
                  {detailQuery.data.payment ? `${detailQuery.data.payment.status} / ${detailQuery.data.payment.provider}` : "Chua co payment"}
                </p>
              </div>
              <div>
                <p className="text-xs uppercase text-muted-foreground">Timeline</p>
                <div className="mt-2 space-y-2">
                  {detailQuery.data.timeline.map((entry) => (
                    <div key={entry.id} className="rounded-lg border border-border px-3 py-2 text-sm">
                      <p className="font-semibold">{entry.eventType}</p>
                      <p className="text-xs text-muted-foreground">{entry.actorType}</p>
                    </div>
                  ))}
                </div>
              </div>
              {detailQuery.data.dispute.status !== "RESOLVED" ? (
                <div>
                  <textarea
                    value={resolutionNote}
                    onChange={(event) => setResolutionNote(event.target.value)}
                    rows={3}
                    className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
                    placeholder="Ghi chu xu ly"
                  />
                  <button
                    type="button"
                    disabled={!resolutionNote.trim() || resolveMutation.isPending}
                    onClick={() => resolveMutation.mutate()}
                    className="mt-2 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                  >
                    Dong khieu nai
                  </button>
                </div>
              ) : null}
            </div>
          ) : null}
        </section>
      </div>
    </AppShell>
  );
}
