import { RoutePlaceholder } from "@/components/rentflow/route-placeholder";

type AdminListingDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function AdminListingDetailPage({ params }: AdminListingDetailPageProps) {
  const { id } = await params;

  return (
    <RoutePlaceholder
      title={`Admin Listing Review: ${id}`}
      description="Static detail shell for listing moderation actions."
      activePath="/admin/listings"
    />
  );
}
