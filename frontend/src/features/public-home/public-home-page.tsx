"use client";

import { useQuery } from "@tanstack/react-query";
import { AppShell } from "@/components/rentflow/app-shell";
import { FeaturedListingsSection } from "@/features/public-home/featured-listings-section";
import { HowItWorksSection } from "@/features/public-home/how-it-works-section";
import { HostCtaSection } from "@/features/public-home/host-cta-section";
import { PublicHero } from "@/features/public-home/public-hero";
import { getFeaturedListings } from "@/features/listings/api";

export function PublicHomePage() {
  const { data: featured = [] } = useQuery({
    queryKey: ["listings", "featured"],
    queryFn: () => getFeaturedListings(3),
  });

  return (
    <AppShell activePath="/">
      <div className="space-y-6">
        <PublicHero />
        <HowItWorksSection />
        <FeaturedListingsSection featured={featured} />
        <HostCtaSection />
      </div>
    </AppShell>
  );
}
