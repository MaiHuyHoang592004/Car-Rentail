import { BookingCreatePageView } from "@/features/bookings/booking-create-page-view";

type BookingCreatePageProps = {
  params: Promise<{ id: string }>;
  searchParams: Promise<{
    guest?: string | string[];
    pickup?: string | string[];
    ret?: string | string[];
  }>;
};

export default async function BookingCreatePage({
  params,
  searchParams,
}: BookingCreatePageProps) {
  const { id } = await params;
  const query = await searchParams;
  const guestRaw = query.guest;
  const guestValue = Array.isArray(guestRaw) ? guestRaw[0] : guestRaw;
  const pickupRaw = query.pickup;
  const retRaw = query.ret;
  const isGuest = guestValue === "1" || guestValue === "true";
  const initialPickupDate = Array.isArray(pickupRaw) ? pickupRaw[0] : pickupRaw;
  const initialReturnDate = Array.isArray(retRaw) ? retRaw[0] : retRaw;

  return (
    <BookingCreatePageView
      listingId={id}
      isGuest={isGuest}
      initialPickupDate={initialPickupDate ?? ""}
      initialReturnDate={initialReturnDate ?? ""}
    />
  );
}
