import { api } from "@/lib/api-client";
import type { ProfileViewModel } from "@/features/profile/types";

interface UserProfileResponse {
  id: string;
  email: string;
  roles: string[];
  fullName: string;
  phone: string;
  dateOfBirth: string | null;
  addressLine: string;
  driverVerificationStatus: string;
}

interface UpdateProfileInput {
  fullName?: string;
  phone?: string;
  dateOfBirth?: string | null;
  addressLine?: string;
}

function mapProfile(raw: UserProfileResponse): ProfileViewModel {
  return {
    id: raw.id,
    email: raw.email,
    roles: raw.roles as ProfileViewModel["roles"],
    fullName: raw.fullName,
    phone: raw.phone,
    dateOfBirth: raw.dateOfBirth ?? "",
    addressLine: raw.addressLine,
    driverVerificationStatus: raw.driverVerificationStatus as ProfileViewModel["driverVerificationStatus"],
  };
}

export async function getProfile(): Promise<ProfileViewModel> {
  return mapProfile(await api.get<UserProfileResponse>("/users/me"));
}

export async function updateProfile(body: UpdateProfileInput): Promise<ProfileViewModel> {
  return mapProfile(await api.patch<UserProfileResponse>("/users/me", body));
}
