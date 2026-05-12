import { HostListingDetailPageView } from "@/features/host/listings/host-listing-detail-page-view";

type HostListingDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function HostListingDetailPage({ params }: HostListingDetailPageProps) {
  const { id } = await params;

  return <HostListingDetailPageView listingId={id} />;
}
