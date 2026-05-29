import { AdminListingDetailPageView } from "@/features/admin/listings/admin-listing-detail-page-view";

type AdminListingDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function AdminListingDetailPage({ params }: AdminListingDetailPageProps) {
  const { id } = await params;
  return <AdminListingDetailPageView listingId={id} />;
}