import { BookingPaymentPageView } from "@/features/payments/booking-payment-page-view";

type BookingPaymentPageProps = {
  params: Promise<{ id: string }>;
};

export default async function BookingPaymentPage({ params }: BookingPaymentPageProps) {
  const { id } = await params;
  return <BookingPaymentPageView bookingId={id} />;
}