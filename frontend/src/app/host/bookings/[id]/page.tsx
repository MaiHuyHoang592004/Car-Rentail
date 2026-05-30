import { HostBookingDetailPageView } from "@/features/host/bookings/host-booking-detail-page-view";

type HostBookingDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function HostBookingDetailPage({ params }: HostBookingDetailPageProps) {
  const { id } = await params;
  return <HostBookingDetailPageView bookingId={id} />;
}
