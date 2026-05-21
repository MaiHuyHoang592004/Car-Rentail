import { AlertCircle } from "lucide-react";

import { ApiError } from "@/lib/api-error";

type ApiErrorPanelProps = {
  error?: ApiError | { code: string; message: string; correlationId?: string };
  code?: string;
  message?: string;
  correlationId?: string;
};

export function ApiErrorPanel(props: ApiErrorPanelProps) {
  const code = props.error?.code ?? props.code ?? "ERROR";
  const message = props.error?.message ?? props.message ?? "Đã có lỗi xảy ra.";
  const correlationId = props.error?.correlationId ?? props.correlationId;

  return (
    <section className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-900">
      <div className="flex items-start gap-3">
        <AlertCircle className="mt-0.5 size-4 shrink-0" />
        <div className="space-y-1">
          <p className="text-sm font-semibold">{code}</p>
          <p className="text-sm">{message}</p>
          {correlationId ? (
            <p className="text-xs text-red-700">Correlation ID: {correlationId}</p>
          ) : null}
        </div>
      </div>
    </section>
  );
}
