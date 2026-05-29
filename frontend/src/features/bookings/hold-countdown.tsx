"use client";

import { useEffect, useMemo, useRef, useState } from "react";

function getRemainingSeconds(expiresAt: string): number {
  const expiresAtMs = Date.parse(expiresAt);
  if (Number.isNaN(expiresAtMs)) {
    return 0;
  }
  const diff = Math.floor((expiresAtMs - Date.now()) / 1000);
  return diff > 0 ? diff : 0;
}

function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

type HoldCountdownProps = {
  expiresAt: string;
  onExpire?: () => void;
};

export function HoldCountdown({ expiresAt, onExpire }: HoldCountdownProps) {
  const [remainingSeconds, setRemainingSeconds] = useState<number>(() =>
    getRemainingSeconds(expiresAt),
  );
  const firedRef = useRef<boolean>(false);

  useEffect(() => {
    firedRef.current = false;
    const timer = window.setInterval(() => {
      setRemainingSeconds(getRemainingSeconds(expiresAt));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [expiresAt]);

  useEffect(() => {
    if (remainingSeconds === 0 && !firedRef.current && onExpire) {
      firedRef.current = true;
      onExpire();
    }
  }, [remainingSeconds, onExpire]);

  const label = useMemo(() => {
    if (remainingSeconds === 0) {
      return "Da het han";
    }
    return formatCountdown(remainingSeconds);
  }, [remainingSeconds]);

  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
      <p className="font-semibold">Giu cho: {label}</p>
      <p className="mt-0.5 text-xs">Het han luc {new Date(expiresAt).toLocaleString("vi-VN")}</p>
    </div>
  );
}
