import { BookingCreatePageView } from "@/features/bookings/booking-create-page-view";

type BookingCreatePageProps = {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ guest?: string | string[] }>;
};

export default async function BookingCreatePage({
  params,
  searchParams,
}: BookingCreatePageProps) {
  const { id } = await params;
  const query = await searchParams;
  const guestRaw = query.guest;
  const guestValue = Array.isArray(guestRaw) ? guestRaw[0] : guestRaw;
  const isGuest = guestValue === "1" || guestValue === "true";

  return <BookingCreatePageView listingId={id} isGuest={isGuest} />;
}
