import { RoutePlaceholder } from "@/components/rentflow/route-placeholder";

export default function AdminUsersPage() {
  return (
    <RoutePlaceholder
      title="Người dùng"
      description="Giao diện quản lý người dùng tĩnh với bộ lọc theo trạng thái/vai trò."
      activePath="/admin/users"
    />
  );
}
