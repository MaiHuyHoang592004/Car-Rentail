"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";

import { AppShell } from "@/components/rentflow/app-shell";
import { ApiError } from "@/lib/api-error";
import { submitDriverLicense } from "@/features/onboarding/api";
import { FriendlyError, OnboardingHero } from "@/features/onboarding/onboarding-components";

type FormState = {
  licenseNumber: string;
  licenseExpiryDate: string;
  documentFileId: string;
};

type FormErrors = Partial<Record<keyof FormState, string>>;

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function DriverLicensePageView() {
  const router = useRouter();
  const [form, setForm] = useState<FormState>({
    licenseNumber: "",
    licenseExpiryDate: "",
    documentFileId: "",
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState<string>("");
  const mutation = useMutation({
    mutationFn: submitDriverLicense,
    onSuccess: () => router.replace("/onboarding/customer/driver-license/pending"),
    onError: (error) => {
      if (error instanceof ApiError) {
        setErrors({
          licenseNumber: error.fieldError("licenseNumber"),
          licenseExpiryDate: error.fieldError("licenseExpiryDate"),
          documentFileId: error.fieldError("documentFileId"),
        });
        setSubmitError(friendlyDriverError(error));
      } else {
        setSubmitError("Không thể gửi GPLX. Vui lòng thử lại.");
      }
    },
  });

  function updateField(field: keyof FormState, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
    setSubmitError("");
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validate(form);
    setErrors(nextErrors);
    setSubmitError("");
    if (Object.keys(nextErrors).length > 0) return;
    mutation.mutate({
      licenseNumber: form.licenseNumber.trim(),
      licenseExpiryDate: form.licenseExpiryDate,
      documentFileId: form.documentFileId.trim(),
    });
  }

  return (
    <AppShell activePath="/onboarding/customer/driver-license">
      <div className="space-y-6">
        <OnboardingHero
          eyebrow="Xác minh GPLX"
          title="Gửi thông tin giấy phép lái xe"
          description="Thông tin này giúp RentFlow xác nhận điều kiện thuê xe trước khi bạn tạo booking."
        />

        {submitError ? <FriendlyError message={submitError} /> : null}

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <div>
              <label htmlFor="licenseNumber" className="mb-1 block text-sm font-semibold text-slate-900">Số GPLX</label>
              <input
                id="licenseNumber"
                value={form.licenseNumber}
                onChange={(event) => updateField("licenseNumber", event.target.value)}
                className="h-11 w-full rounded-xl border border-slate-300 bg-white px-3 text-sm outline-none ring-blue-200 focus:ring-4"
                placeholder="Ví dụ: B1234567"
              />
              {errors.licenseNumber ? <p className="mt-1 text-xs text-rose-700">{errors.licenseNumber}</p> : null}
            </div>

            <div>
              <label htmlFor="licenseExpiryDate" className="mb-1 block text-sm font-semibold text-slate-900">Ngày hết hạn GPLX</label>
              <input
                id="licenseExpiryDate"
                type="date"
                value={form.licenseExpiryDate}
                onChange={(event) => updateField("licenseExpiryDate", event.target.value)}
                className="h-11 w-full rounded-xl border border-slate-300 bg-white px-3 text-sm outline-none ring-blue-200 focus:ring-4"
              />
              {errors.licenseExpiryDate ? (
                <p className="mt-1 text-xs text-rose-700">{errors.licenseExpiryDate}</p>
              ) : null}
            </div>

            <div>
              <label htmlFor="documentFileId" className="mb-1 block text-sm font-semibold text-slate-900">Mã tài liệu GPLX demo</label>
              <input
                id="documentFileId"
                value={form.documentFileId}
                onChange={(event) => updateField("documentFileId", event.target.value)}
                className="h-11 w-full rounded-xl border border-slate-300 bg-white px-3 text-sm outline-none ring-blue-200 focus:ring-4"
                placeholder="Ví dụ: 550e8400-e29b-41d4-a716-446655440000"
              />
              <p className="mt-1 text-xs text-slate-500">
                Nhập UUID gồm 5 nhóm ký tự theo dạng 8-4-4-4-12, ví dụ
                {" "}
                <span className="font-mono">550e8400-e29b-41d4-a716-446655440000</span>.
                Demo/dev chấp nhận UUID đúng định dạng; preview chỉ hiển thị khi UUID trỏ tới file đã upload thật.
              </p>
              {/* TODO: Replace demo documentFileId input with the real GPLX upload flow when that API exists. */}
              {errors.documentFileId ? <p className="mt-1 text-xs text-rose-700">{errors.documentFileId}</p> : null}
            </div>

            <button
              type="submit"
              disabled={mutation.isPending}
              className="rounded-full bg-blue-600 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {mutation.isPending ? "Đang gửi..." : "Gửi GPLX để duyệt"}
            </button>
          </form>
        </section>
      </div>
    </AppShell>
  );
}

function validate(form: FormState): FormErrors {
  const errors: FormErrors = {};
  if (!form.licenseNumber.trim()) {
    errors.licenseNumber = "Vui lòng nhập số GPLX.";
  }
  if (!form.licenseExpiryDate) {
    errors.licenseExpiryDate = "Vui lòng chọn ngày hết hạn.";
  } else if (Date.parse(`${form.licenseExpiryDate}T00:00:00`) < Date.parse(new Date().toISOString().slice(0, 10))) {
    errors.licenseExpiryDate = "Ngày hết hạn phải là hôm nay hoặc trong tương lai.";
  }
  if (!UUID_RE.test(form.documentFileId.trim())) {
    errors.documentFileId = "Mã tài liệu demo phải là UUID hợp lệ.";
  }
  return errors;
}

function friendlyDriverError(error: ApiError): string {
  if (error.status === 404) {
    return "Không tìm thấy endpoint xác minh GPLX. Kiểm tra API/rewrite.";
  }
  if (error.details.length > 0) {
    return "Một số thông tin GPLX chưa hợp lệ. Vui lòng kiểm tra lại.";
  }
  switch (error.code) {
    case "FORBIDDEN":
    case "ACCESS_DENIED":
      return "Tài khoản cần có vai trò khách thuê để gửi GPLX.";
    case "RATE_LIMIT_EXCEEDED":
      return "Bạn thao tác quá nhanh. Vui lòng thử lại sau.";
    default:
      return "Không thể gửi GPLX. Vui lòng thử lại.";
  }
}
