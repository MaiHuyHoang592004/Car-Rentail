import type { NextConfig } from "next";

const API_BASE = process.env.API_BACKEND_URL ?? "http://localhost:8087";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${API_BASE}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
