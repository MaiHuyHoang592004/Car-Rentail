/** * Centralized display labels for all user-facing enums. */import type { BookingStatus } from "@/features/bookings/types";import type { HostListingStatus, HostVehicleStatus, HostAvailabilityStatus } from "@/features/host/types";const BOOKING_STATUS_LABELS: Record<BookingStatus, string> = {  HELD: "Đang giữ chỗ",  PENDING_HOST_APPROVAL: "Chờ chủ xe duyệt",  CONFIRMED: "Đã xác nhận",  IN_PROGRESS: "Đang diễn ra",  COMPLETED: "Hoàn thành",  CANCELLED: "Đã hủy",  REJECTED: "Bị từ chối",  EXPIRED: "Hết hạn",};export function getBookingStatusLabel(status: BookingStatus): string {  return BOOKING_STATUS_LABELS[status] ?? status;}const LISTING_STATUS_LABELS: Record<HostListingStatus, string> = {  DRAFT: "Nháp",  PENDING_APPROVAL: "Chờ duyệt",  ACTIVE: "Hoạt động",  SUSPENDED: "Bị tạm ngưng",  ARCHIVED: "Đã lưu trữ",};export function getListingStatusLabel(status: HostListingStatus): string {  return LISTING_STATUS_LABELS[status] ?? status;}const VEHICLE_STATUS_LABELS: Record<HostVehicleStatus, string> = {  DRAFT: "Nháp",  ACTIVE: "Hoạt động",  MAINTENANCE: "Bảo trì",  SUSPENDED: "Bị tạm ngưng",  ARCHIVED: "Đã lưu trữ",};export function getVehicleStatusLabel(status: HostVehicleStatus): string {  return VEHICLE_STATUS_LABELS[status] ?? status;}const AVAILABILITY_STATUS_LABELS: Record<string, string> = {  FREE: "Trống",  HOLD: "Đang giữ",  BOOKED: "Đã đặt",  BLOCKED: "Đã chặn",  UNAVAILABLE: "Không khả dụng",};export function getAvailabilityStatusLabel(status: string): string {  return AVAILABILITY_STATUS_LABELS[status] ?? status;}const TRANSMISSION_LABELS: Record<string, string> = {  AUTO: "Tự động",  MANUAL: "Số sàn",};export function getTransmissionLabel(transmission: string): string {  return TRANSMISSION_LABELS[transmission] ?? transmission;}const FUEL_TYPE_LABELS: Record<string, string> = {  GASOLINE: "Xăng",  DIESEL: "Dầu",  EV: "Điện",  PETROL: "Xăng",  ELECTRIC: "Điện",  HYBRID: "Hybrid",};export function getFuelTypeLabel(fuelType: string): string {  return FUEL_TYPE_LABELS[fuelType] ?? fuelType;}const CANCELLATION_POLICY_LABELS: Record<string, string> = {  FLEXIBLE: "Linh hoạt",  MODERATE: "Trung bình",  STRICT: "Nghiêm ngặt",};export function getCancellationPolicyLabel(policy: string): string {  return CANCELLATION_POLICY_LABELS[policy] ?? policy;}export function getListingActionHint(status: HostListingStatus): string {  switch (status) {    case "DRAFT": return "Chỉnh sửa · Gửi duyệt · Lưu trữ";    case "PENDING_APPROVAL": return "Xem · Lưu trữ";    case "ACTIVE": return "Xem · Lưu trữ";    case "SUSPENDED": return "Xem · Kích hoạt lại";    case "ARCHIVED": return "Chỉ xem";    default: return "Xem";  }}


const PAYMENT_STATUS_LABELS: Record<string, string> = {
  UNPAID: "Chua thanh toan",
  AUTHORIZED: "Da giu tien",
  CAPTURED: "Da thanh toan",
  PARTIALLY_REFUNDED: "Hoan tien mot phan",
  REFUNDED: "Da hoan tien",
  VOIDED: "Da huy thanh toan",
  FAILED: "Thanh toan that bai",
};

export function getPaymentStatusLabel(status: string): string {
  return PAYMENT_STATUS_LABELS[status] ?? status;
}

const PAYMENT_METHOD_LABELS: Record<string, string> = {
  COREBANK_TRANSFER: "CoreBank",
  BANK_TRANSFER_QR: "Chuyen khoan / VietQR",
};

export function getPaymentMethodLabel(method: string | null): string {
  if (!method) return "—";
  return PAYMENT_METHOD_LABELS[method] ?? method;
}