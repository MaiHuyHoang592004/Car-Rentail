import { RoutePlaceholder } from "@/components/rentflow/route-placeholder";

export default function AdminListingsPage() {
  return (
    <RoutePlaceholder
      title="Listing Approval Queue"
      description="Static queue shell for approve/reject/suspend/re-activate listing actions."
      activePath="/admin/listings"
    />
  );
}
