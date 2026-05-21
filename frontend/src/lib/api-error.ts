export type ApiFieldError = {
  field: string;
  message: string;
};

export type ApiErrorPayload = {
  code: string;
  message: string;
  details?: ApiFieldError[];
  correlationId?: string;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: ApiFieldError[];
  readonly correlationId?: string;

  constructor(status: number, payload: ApiErrorPayload) {
    super(payload.message || payload.code || `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.code = payload.code || "UNKNOWN_ERROR";
    this.details = payload.details ?? [];
    this.correlationId = payload.correlationId;
  }

  fieldError(field: string): string | undefined {
    return this.details.find((d) => d.field === field)?.message;
  }

  static network(message = "Network request failed"): ApiError {
    return new ApiError(0, { code: "NETWORK_ERROR", message });
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError;
}
