import { RoutePlaceholder } from "@/components/rentflow/route-placeholder";

export default function AdminListingsPage() {
  return (
    <RoutePlaceholder
      title="Hàng chờ duyệt tin đăng"
      description="Giao diện tĩnh để duyệt/từ chối/tạm ngưng/kích hoạt lại tin đăng."
      activePath="/admin/listings"
    />
  );
}
