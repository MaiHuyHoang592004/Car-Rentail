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
  const [remainingSeconds, setRemainingSeconds] = useState<number>(() => getRemainingSeconds(expiresAt));
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
      return "Expired";
    }
    return formatCountdown(remainingSeconds);
  }, [remainingSeconds]);

  return (
    <div className="rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-2 text-sm text-indigo-900">
      <p className="font-semibold">Hold countdown: {label}</p>
      <p className="mt-0.5 text-xs text-indigo-700">Expires at {new Date(expiresAt).toLocaleString()}</p>
    </div>
  );
}
