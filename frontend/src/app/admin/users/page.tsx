import { RoutePlaceholder } from "@/components/rentflow/route-placeholder";

export default function AdminUsersPage() {
  return (
    <RoutePlaceholder
      title="Admin Users"
      description="Static user management table shell with status/role filters."
      activePath="/admin/users"
    />
  );
}
