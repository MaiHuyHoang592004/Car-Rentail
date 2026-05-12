import { ListingDetailPageView } from "@/features/listings/listing-detail-page-view";

type ListingDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function ListingDetailPage({ params }: ListingDetailPageProps) {
  const { id } = await params;

  return <ListingDetailPageView listingId={id} />;
}
