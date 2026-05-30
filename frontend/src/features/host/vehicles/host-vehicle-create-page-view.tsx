"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { ArrowLeft, Plus } from "lucide-react";

import { WorkspaceSidebar } from "@/components/rentflow/workspace-sidebar";
import { HostWorkspaceNav } from "@/features/host/components/host-workspace-nav";
import { vehicleFormSchema, type VehicleFormState } from "@/features/host/forms";
import { addHostVehiclePhoto, createHostVehicle } from "@/features/host/vehicles/api";
import { VehicleFormFields } from "@/features/host/vehicles/vehicle-form-fields";
import {
  VehiclePhotoUpload,
  type VehiclePhotoDraft,
} from "@/features/host/vehicles/vehicle-photo-upload";

const INITIAL_FORM: VehicleFormState = {
  category: "SEDAN",
  make: "",
  model: "",
  year: "",
  transmission: "AUTO",
  fuelType: "PETROL",
  seats: "5",
  status: "DRAFT",
  city: "",
  plateNumber: "",
  vin: "",
};

export function HostVehicleCreatePageView() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [submitError, setSubmitError] = useState<string>("");
  const [photoError, setPhotoError] = useState<string>("");
  const [photos, setPhotos] = useState<VehiclePhotoDraft[]>([]);
  const photosRef = useRef<VehiclePhotoDraft[]>([]);

  const form = useForm<VehicleFormState>({
    resolver: zodResolver(vehicleFormSchema),
    defaultValues: INITIAL_FORM,
  });

  useEffect(() => {
    photosRef.current = photos;
  }, [photos]);

  useEffect(() => {
    return () => {
      photosRef.current.forEach((photo) => URL.revokeObjectURL(photo.previewUrl));
    };
  }, []);

  const { mutate: doCreate, isPending: creating } = useMutation({
    mutationFn: async (body: Parameters<typeof createHostVehicle>[0]) => {
      const vehicleId = await createHostVehicle(body);
      const failedUploads = await uploadSelectedPhotos(vehicleId);
      return { vehicleId, failedUploads };
    },
    onSuccess: ({ vehicleId, failedUploads }, variables) => {
      queryClient.invalidateQueries({ queryKey: ["host", "vehicles"] });
      if (failedUploads > 0) {
        const message = "Xe đã được tạo, một số ảnh tải lên thất bại. Vui lòng thử lại ở chi tiết xe.";
        setSubmitError(message);
        toast.error(message);
        router.push(`/host/vehicles/${vehicleId}`);
        return;
      }
      toast.success("Da tao xe thanh cong.");
      form.reset(INITIAL_FORM);
      resetPhotos();
      router.push(`/host/vehicles?status=${variables.status ?? "DRAFT"}`);
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : "Loi khi tao xe.";
      setSubmitError(message);
      toast.error("Loi khi tao xe. Vui long thu lai.");
    },
  });

  function handleSubmit(values: VehicleFormState) {
    setSubmitError("");
    doCreate({
      category: values.category.trim(),
      make: values.make.trim(),
      model: values.model.trim(),
      year: Number(values.year),
      plateNumber: values.plateNumber.trim(),
      vin: values.vin.trim() || undefined,
      transmission: values.transmission,
      fuelType: values.fuelType.trim(),
      seats: Number(values.seats),
      city: values.city.trim(),
      status: values.status,
    });
  }

  async function uploadSelectedPhotos(vehicleId: string): Promise<number> {
    let failed = 0;
    const selectedPhotos = photosRef.current;
    for (const [index, photo] of selectedPhotos.entries()) {
      try {
        await addHostVehiclePhoto(vehicleId, {
          bucket: "rentflow-local",
          objectKey: buildVehiclePhotoObjectKey(vehicleId, photo.file.name),
          contentType: photo.file.type || "image/jpeg",
          sizeBytes: photo.file.size,
          primary: photo.primary || index === 0,
        });
      } catch {
        failed += 1;
      }
    }
    return failed;
  }

  function resetPhotos() {
    setPhotos((current) => {
      current.forEach((photo) => URL.revokeObjectURL(photo.previewUrl));
      return [];
    });
    setPhotoError("");
  }

  function handleAddPhotos(files: FileList | null) {
    if (!files) return;
    setPhotoError("");

    const incoming = Array.from(files);
    const validPhotos: VehiclePhotoDraft[] = [];

    for (const file of incoming) {
      if (!file.type.startsWith("image/")) {
        setPhotoError("Chỉ nhận file ảnh PNG, JPG hoặc WEBP.");
        continue;
      }
      if (file.size > 10 * 1024 * 1024) {
        setPhotoError("Mỗi ảnh xe phải nhỏ hơn hoặc bằng 10MB.");
        continue;
      }
      validPhotos.push({
        id: `${Date.now()}-${file.name}-${Math.random().toString(36).slice(2)}`,
        file,
        previewUrl: URL.createObjectURL(file),
        primary: false,
      });
    }

    if (validPhotos.length === 0) return;

    setPhotos((current) => {
      const remainingSlots = Math.max(0, 8 - current.length);
      const nextPhotos = validPhotos.slice(0, remainingSlots);
      validPhotos.slice(remainingSlots).forEach((photo) => URL.revokeObjectURL(photo.previewUrl));
      if (nextPhotos.length < validPhotos.length) {
        setPhotoError("Chỉ có thể thêm tối đa 8 ảnh xe.");
      }
      const merged = [...current, ...nextPhotos];
      if (!merged.some((photo) => photo.primary) && merged[0]) {
        return merged.map((photo, index) => ({ ...photo, primary: index === 0 }));
      }
      return merged;
    });
  }

  function handleRemovePhoto(id: string) {
    setPhotos((current) => {
      const removed = current.find((photo) => photo.id === id);
      if (removed) URL.revokeObjectURL(removed.previewUrl);
      const remaining = current.filter((photo) => photo.id !== id);
      if (removed?.primary && remaining[0]) {
        return remaining.map((photo, index) => ({ ...photo, primary: index === 0 }));
      }
      return remaining;
    });
  }

  function handlePrimaryPhotoChange(id: string) {
    setPhotos((current) =>
      current.map((photo) => ({
        ...photo,
        primary: photo.id === id,
      })),
    );
  }

  const primaryPhoto = photos.find((photo) => photo.primary) ?? photos[0] ?? null;

  return (
    <WorkspaceSidebar sidebar={<HostWorkspaceNav />} activePath="/host/vehicles">
      <div className="space-y-6">
        <section className="rf-section-card p-6 md:p-8">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Them xe moi</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Chon san cau hinh co san de giam nhap tay va tao xe dung luong host.
              </p>
            </div>
            <Link
              href="/host/vehicles"
              className="flex items-center gap-1.5 rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
            >
              <ArrowLeft className="h-4 w-4" />
              Quay lai
            </Link>
          </div>
        </section>

        {submitError ? (
          <section className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-900">
            {submitError}
          </section>
        ) : null}

        <section className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div className="rf-section-card p-6">
            <form onSubmit={form.handleSubmit(handleSubmit)} noValidate className="space-y-6">
              <VehiclePhotoUpload
                photos={photos}
                error={photoError}
                onAdd={handleAddPhotos}
                onRemove={handleRemovePhoto}
                onPrimaryChange={handlePrimaryPhotoChange}
              />

              <VehicleFormFields
                register={form.register}
                setValue={form.setValue}
                watch={form.watch}
                errors={form.formState.errors}
                createMode={true}
              />

              <div className="flex flex-wrap gap-2">
                <button
                  type="submit"
                  disabled={creating}
                  className="flex items-center gap-2 rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Plus className="h-4 w-4" />
                  {creating
                    ? "Dang tao xe..."
                    : form.watch("status") === "ACTIVE"
                      ? "Tao va kich hoat"
                      : "Luu vao Draft"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                  form.reset(INITIAL_FORM);
                  setSubmitError("");
                  resetPhotos();
                }}
                  className="rounded-full border border-border bg-background px-5 py-2.5 text-sm font-semibold text-foreground hover:bg-accent"
                >
                  Dat lai
                </button>
              </div>
            </form>
          </div>

          <aside className="rf-section-card h-fit p-5 lg:sticky lg:top-24">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary/80">
              Preview
            </p>
            <div className="mt-4 overflow-hidden rounded-2xl bg-muted">
              {primaryPhoto ? (
                <img
                  src={primaryPhoto.previewUrl}
                  alt={primaryPhoto.file.name}
                  className="aspect-[4/3] w-full object-cover"
                />
              ) : (
                <div className="aspect-[4/3] bg-[linear-gradient(135deg,#dbe1ff_0%,#f7f9fb_45%,#d5e3fc_100%)]" />
              )}
            </div>
            <div className="mt-4 space-y-2">
              <p className="text-lg font-bold text-foreground">
                {form.watch("make") || "Hang xe"} {form.watch("model") || "Dong xe"}
              </p>
              <p className="text-sm text-muted-foreground">
                {form.watch("year") || "Nam san xuat"} · {form.watch("city") || "Thanh pho"}
              </p>
              <div className="flex flex-wrap gap-2 pt-2">
                <span className="rounded-full bg-primary/8 px-3 py-1 text-xs font-semibold text-primary">
                  {form.watch("transmission") === "MANUAL" ? "So san" : "Tu dong"}
                </span>
                <span className="rounded-full bg-muted px-3 py-1 text-xs font-semibold text-foreground">
                  {form.watch("fuelType") || "Nhien lieu"}
                </span>
                <span className="rounded-full bg-muted px-3 py-1 text-xs font-semibold text-foreground">
                  {form.watch("seats") || "0"} cho
                </span>
              </div>
              <p className="pt-2 text-xs text-muted-foreground">
                Xe se duoc dua ve tab{" "}
                <span className="font-semibold text-foreground">
                  {form.watch("status") === "ACTIVE" ? "Active" : "Draft"}
                </span>{" "}
                sau khi tao.
              </p>
            </div>
          </aside>
        </section>
      </div>
    </WorkspaceSidebar>
  );
}

function buildVehiclePhotoObjectKey(vehicleId: string, fileName: string): string {
  const safeName = fileName.toLowerCase().replace(/[^a-z0-9._-]+/g, "-").slice(-80);
  const uniquePart = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `vehicles/${vehicleId}/${uniquePart}-${safeName}`;
}
