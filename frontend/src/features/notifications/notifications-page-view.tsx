"use client";

import { Bell, CheckCheck } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { PageHeader } from "@/components/rentflow/page-header";
import { Button } from "@/components/rentflow/ui/button";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import {
  listMyNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/features/notifications/api";

export function NotificationsPageView() {
  const queryClient = useQueryClient();
  const notificationsQuery = useQuery({
    queryKey: ["notifications"],
    queryFn: ({ signal }) => listMyNotifications(signal),
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["notifications"] });

  const markReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: refresh,
  });
  const markAllMutation = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: refresh,
  });

  if (notificationsQuery.isLoading) {
    return <PageSkeleton message="Dang tai thong bao..." />;
  }

  const notifications = notificationsQuery.data?.content ?? [];
  const unreadCount = notifications.filter((item) => !item.readAt).length;

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-8">
      <PageHeader
        title="Thong bao"
        description={`${unreadCount} thong bao chua doc`}
        actions={
          <Button
            disabled={unreadCount === 0 || markAllMutation.isPending}
            onClick={() => markAllMutation.mutate()}
            type="button"
            variant="outline"
          >
            <CheckCheck className="h-4 w-4" aria-hidden />
            Danh dau da doc
          </Button>
        }
      />

      <section className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
        {notifications.length === 0 ? (
          <div className="flex items-center gap-3 px-4 py-8 text-sm text-slate-500">
            <Bell className="h-5 w-5" aria-hidden />
            Chua co thong bao.
          </div>
        ) : (
          notifications.map((notification) => (
            <article
              className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-start sm:justify-between"
              key={notification.id}
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  {!notification.readAt ? (
                    <span className="h-2 w-2 rounded-full bg-emerald-500" aria-label="Chua doc" />
                  ) : null}
                  <h2 className="text-sm font-semibold text-slate-950">{notification.title}</h2>
                </div>
                <p className="text-sm text-slate-600">{notification.message}</p>
                <p className="text-xs text-slate-400">{new Date(notification.createdAt).toLocaleString()}</p>
              </div>
              {!notification.readAt ? (
                <Button
                  disabled={markReadMutation.isPending}
                  onClick={() => markReadMutation.mutate(notification.id)}
                  type="button"
                  variant="ghost"
                >
                  Da doc
                </Button>
              ) : null}
            </article>
          ))
        )}
      </section>
    </main>
  );
}
