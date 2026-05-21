import { AlertCircle } from "lucide-react";

import { ApiError } from "@/lib/api-error";
import { cn } from "@/lib/utils";

type ErrorBannerProps = {
  error: unknown;
  title?: string;
  className?: string;
};

function normalize(error: unknown): { code?: string; message: string; correlationId?: string } {
  if (error instanceof ApiError) {
    return { code: error.code, message: error.message, correlationId: error.correlationId };
  }
  if (error instanceof Error) {
    return { message: error.message };
  }
  if (typeof error === "string") {
    return { message: error };
  }
  return { message: "Đã có lỗi xảy ra." };
}

export function ErrorBanner({ error, title, className }: ErrorBannerProps) {
  if (!error) return null;
  const { code, message, correlationId } = normalize(error);
  return (
    <section
      role="alert"
      className={cn(
        "flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-red-900",
        className,
      )}
    >
      <AlertCircle className="mt-0.5 size-4 shrink-0" />
      <div className="space-y-0.5 text-sm">
        {title ? <p className="font-semibold">{title}</p> : null}
        <p>{message}</p>
        {code || correlationId ? (
          <p className="text-xs text-red-700">
            {code ? <span className="font-mono">{code}</span> : null}
            {code && correlationId ? <span> · </span> : null}
            {correlationId ? <span>ID: {correlationId}</span> : null}
          </p>
        ) : null}
      </div>
    </section>
  );
}
