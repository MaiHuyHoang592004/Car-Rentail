"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";

import { AppShell } from "@/components/rentflow/app-shell";
import { useAuth } from "@/features/auth/auth-context";
import { getProfile } from "@/features/profile/api";
import { verifyEmailToken } from "@/features/onboarding/api";
import { FriendlyError, OnboardingHero, StatusPanel } from "@/features/onboarding/onboarding-components";
import { customerReadyToBook } from "@/features/onboarding/model";
import { ApiError } from "@/lib/api-error";

type VerifyStatus = "verifying" | "success" | "error";

export function VerifyEmailPageView() {
  const params = useSearchParams();
  const token = params.get("token") ?? "";
  const { status } = useAuth();
  const submittedTokenRef = useRef<string | null>(null);
  const [verifyStatus, setVerifyStatus] = useState<VerifyStatus>(token ? "verifying" : "error");
  const [verifyError, setVerifyError] = useState<unknown>(null);

  useEffect(() => {
    if (!token) return;

    if (submittedTokenRef.current === token) {
      return;
    }

    submittedTokenRef.current = token;
    setVerifyStatus("verifying");
    setVerifyError(null);

    let cancelled = false;
    void verifyEmailToken(token)
      .then(() => {
        if (!cancelled) {
          setVerifyStatus("success");
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setVerifyStatus((current) => (current === "success" ? current : "error"));
          setVerifyError(error);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [token]);

  const verifyState = !token
    ? "error"
    : verifyStatus;

  const errorMessage = !token
    ? "Liên kết xác minh không hợp lệ hoặc thiếu token."
    : friendlyVerifyError(verifyError);

  const profileQuery = useQuery({
    queryKey: ["profile", "after-email-verify"],
    queryFn: getProfile,
    enabled: verifyState === "success" && status === "authenticated",
    retry: false,
  });

  const cta = useMemo(() => {
    if (status !== "authenticated" || !profileQuery.data) {
      return { href: "/login", label: "Đăng nhập" };
    }
    const profile = profileQuery.data;
    if (profile.roles.includes("CUSTOMER")) {
      return { href: customerReadyToBook(profile) ? "/listings" : "/onboarding/customer", label: "Tiếp tục onboarding" };
    }
    if (profile.roles.includes("HOST")) {
      return { href: "/onboarding/host", label: "Tiếp tục onboarding" };
    }
    return { href: "/me/profile", label: "Mở hồ sơ" };
  }, [profileQuery.data, status]);

  return (
    <AppShell activePath="/verify-email">
      <div className="space-y-6">
        <OnboardingHero
          eyebrow="Xác minh email"
          title={verifyState === "success" ? "Email đã được xác minh" : "Đang kiểm tra liên kết"}
          description="Kết quả xác minh email không yêu cầu phiên đăng nhập. Nếu bạn đang đăng xuất, hãy đăng nhập lại để tiếp tục."
        />

        {verifyState === "verifying" ? (
          <StatusPanel
            title="Đang xác minh"
            description="RentFlow đang kiểm tra token trong liên kết email của bạn."
            status="PENDING"
            statusLabel="Đang xử lý"
          />
        ) : null}

        {verifyState === "success" ? (
          <StatusPanel
            title="Xác minh thành công"
            description="Email của bạn đã sẵn sàng cho booking, thanh toán và thông báo tài khoản."
            status="APPROVED"
            statusLabel="Đã xác minh"
            actionHref={cta.href}
            actionLabel={profileQuery.isLoading ? "Đang tải hồ sơ..." : cta.label}
          />
        ) : null}

        {verifyState === "error" ? (
          <>
            <FriendlyError message={errorMessage} />
            <Link
              href="/login"
              className="inline-flex rounded-full bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-700"
            >
              Đăng nhập để gửi lại email
            </Link>
          </>
        ) : null}
      </div>
    </AppShell>
  );
}

function friendlyVerifyError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case "VALIDATION_ERROR":
        return "Liên kết xác minh không hợp lệ.";
      case "INVALID_TOKEN":
      case "EMAIL_VERIFICATION_TOKEN_INVALID":
      case "EMAIL_VERIFICATION_TOKEN_EXPIRED":
      case "TOKEN_INVALID":
      case "TOKEN_EXPIRED":
        return "Liên kết xác minh đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập để gửi lại email.";
      default:
        return "Không thể xác minh email bằng liên kết này. Vui lòng thử lại hoặc gửi lại email xác minh.";
    }
  }
  return "Không thể xác minh email bằng liên kết này. Vui lòng thử lại.";
}
