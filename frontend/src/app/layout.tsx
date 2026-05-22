import type { Metadata } from "next";
import "./globals.css";

import { AppProviders } from "@/components/rentflow/providers";

export const metadata: Metadata = {
  title: "RentFlow",
  description: "Nền tảng cho thuê xe ngang hàng RentFlow",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className="h-full">
      <body className="min-h-full flex flex-col">
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
