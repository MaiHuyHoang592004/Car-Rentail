import { HostListingAvailabilityPageView } from "@/features/host/listings/host-listing-availability-page-view";

type HostListingAvailabilityPageProps = {
  params: Promise<{ id: string }>;
};

export default async function HostListingAvailabilityPage({
  params,
}: HostListingAvailabilityPageProps) {
  const { id } = await params;

  return <HostListingAvailabilityPageView listingId={id} />;
}
