import { ApiError, type ApiErrorPayload } from "@/lib/api-error";
import { api } from "@/lib/api-client";

export type SubmitDriverLicenseInput = {
  licenseNumber: string;
  licenseExpiryDate: string;
  documentFileId: string;
};

export async function verifyEmailToken(token: string): Promise<true> {
  const response = await fetch("/api/v1/auth/verify-email", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ token }),
    credentials: "omit",
  });

  if (!response.ok) {
    throw await parsePublicApiError(response);
  }

  return true;
}

export async function submitDriverLicense(input: SubmitDriverLicenseInput): Promise<void> {
  await api.post("/users/me/driver-license", input);
}

async function parsePublicApiError(response: Response): Promise<ApiError> {
  let payload: ApiErrorPayload;
  try {
    payload = (await response.json()) as ApiErrorPayload;
  } catch {
    payload = {
      code: response.status >= 500 ? "INTERNAL_ERROR" : "REQUEST_FAILED",
      message: response.statusText || "Request failed",
    };
  }
  return new ApiError(response.status, payload);
}
