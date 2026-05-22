import { toast } from "sonner";

import { ApiError } from "@/lib/api-error";

export type ErrorHandlerConfig = {
  /** Map error code -> custom handler. Wins over onFieldError/onUnknown. */
  onCode?: Partial<Record<string, (err: ApiError) => void>>;
  /**
   * Per-field handler for 400 VALIDATION_ERROR responses. Called once per
   * detail entry. If `onCode["VALIDATION_ERROR"]` is provided, this is skipped.
   * If no details exist on the error, falls through to onUnknown.
   */
  onFieldError?: (field: string, message: string) => void;
  /** Fallback for ApiError not matched by onCode/onFieldError. */
  onUnknown?: (err: ApiError) => void;
  /** Fallback for non-ApiError (network failure, thrown string, etc.). */
  onNetwork?: (err: unknown) => void;
};

const DEFAULT_NETWORK_MESSAGE = "Lỗi kết nối, vui lòng thử lại.";

function defaultUnknown(err: ApiError): void {
  toast.error(err.message || "Đã xảy ra lỗi.");
}

function defaultNetwork(_err: unknown): void {
  toast.error(DEFAULT_NETWORK_MESSAGE);
}

/**
 * Dispatch an error from a mutation/query to the appropriate handler.
 *
 * Resolution order:
 * 1. If `err` is not an ApiError → `onNetwork` (default: toast).
 * 2. If `onCode[err.code]` exists → call it and return.
 * 3. If `err.code === "VALIDATION_ERROR"` and `onFieldError` provided →
 *    call `onFieldError(field, message)` for every detail. If there are no
 *    details, fall through to `onUnknown`.
 * 4. Otherwise → `onUnknown` (default: toast with err.message).
 */
export function handleApiError(err: unknown, config: ErrorHandlerConfig): void {
  if (!(err instanceof ApiError)) {
    (config.onNetwork ?? defaultNetwork)(err);
    return;
  }

  const codeHandler = config.onCode?.[err.code];
  if (codeHandler) {
    codeHandler(err);
    return;
  }

  if (err.code === "VALIDATION_ERROR" && config.onFieldError && err.details.length > 0) {
    for (const detail of err.details) {
      config.onFieldError(detail.field, detail.message);
    }
    return;
  }

  (config.onUnknown ?? defaultUnknown)(err);
}
