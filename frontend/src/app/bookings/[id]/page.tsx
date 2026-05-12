import { BookingDetailPageView } from "@/features/bookings/booking-detail-page-view";

type BookingDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function BookingDetailPage({ params }: BookingDetailPageProps) {
  const { id } = await params;

  return <BookingDetailPageView bookingId={id} />;
}
