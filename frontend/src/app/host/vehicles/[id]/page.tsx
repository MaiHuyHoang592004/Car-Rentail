import { HostVehicleDetailPageView } from "@/features/host/vehicles/host-vehicle-detail-page-view";

type HostVehicleDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function HostVehicleDetailPage({ params }: HostVehicleDetailPageProps) {
  const { id } = await params;

  return <HostVehicleDetailPageView vehicleId={id} />;
}
