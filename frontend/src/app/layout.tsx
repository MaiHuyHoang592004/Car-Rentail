import type { Metadata } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import "./globals.css";

import { AppProviders } from "@/components/rentflow/providers";

const beVietnamPro = Be_Vietnam_Pro({
  subsets: ["latin", "vietnamese"],
  variable: "--font-sans",
  weight: ["400", "500", "600", "700"],
});

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
      <body className={`${beVietnamPro.variable} min-h-full flex flex-col`}>
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
