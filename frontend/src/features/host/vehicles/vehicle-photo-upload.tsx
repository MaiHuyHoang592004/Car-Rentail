"use client";

import { ImagePlus, Star, Trash2 } from "lucide-react";

export type VehiclePhotoDraft = {
  id: string;
  file: File;
  previewUrl: string;
  primary: boolean;
};

type VehiclePhotoUploadProps = {
  photos: VehiclePhotoDraft[];
  error?: string;
  onAdd: (files: FileList | null) => void;
  onRemove: (id: string) => void;
  onPrimaryChange: (id: string) => void;
};

export function VehiclePhotoUpload({
  photos,
  error,
  onAdd,
  onRemove,
  onPrimaryChange,
}: VehiclePhotoUploadProps) {
  return (
    <section>
      <div className="mb-4">
        <h3 className="text-sm font-bold uppercase tracking-[0.18em] text-muted-foreground">
          Ảnh xe
        </h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Thêm ảnh ngoại thất, nội thất và giấy tờ nhận diện xe. Tối đa 8 ảnh, mỗi ảnh 10MB.
        </p>
      </div>

      <label className="flex min-h-40 cursor-pointer flex-col items-center justify-center rounded-2xl border border-dashed border-border bg-background px-5 py-8 text-center transition-colors hover:bg-accent">
        <span className="flex size-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
          <ImagePlus className="h-6 w-6" />
        </span>
        <span className="mt-3 text-sm font-semibold text-foreground">Chọn ảnh xe</span>
        <span className="mt-1 text-xs text-muted-foreground">PNG, JPG, WEBP</span>
        <input
          type="file"
          accept="image/png,image/jpeg,image/webp"
          multiple
          className="sr-only"
          onChange={(event) => {
            onAdd(event.target.files);
            event.target.value = "";
          }}
        />
      </label>

      {error ? <p className="mt-2 text-xs text-rose-700">{error}</p> : null}

      {photos.length > 0 ? (
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          {photos.map((photo) => (
            <div key={photo.id} className="overflow-hidden rounded-2xl border border-border bg-background">
              <div className="relative aspect-[4/3] bg-muted">
                <img
                  src={photo.previewUrl}
                  alt={photo.file.name}
                  className="h-full w-full object-cover"
                />
                {photo.primary ? (
                  <span className="absolute left-2 top-2 rounded-full bg-primary px-2.5 py-1 text-[11px] font-semibold text-primary-foreground">
                    Ảnh bìa
                  </span>
                ) : null}
              </div>
              <div className="flex items-center justify-between gap-2 p-3">
                <div className="min-w-0">
                  <p className="truncate text-xs font-semibold text-foreground">{photo.file.name}</p>
                  <p className="text-[11px] text-muted-foreground">
                    {(photo.file.size / 1024 / 1024).toFixed(1)} MB
                  </p>
                </div>
                <div className="flex shrink-0 gap-1">
                  <button
                    type="button"
                    onClick={() => onPrimaryChange(photo.id)}
                    className="inline-flex size-8 items-center justify-center rounded-lg border border-border text-muted-foreground hover:bg-accent hover:text-primary"
                    aria-label="Đặt làm ảnh bìa"
                  >
                    <Star className={photo.primary ? "h-4 w-4 fill-current text-primary" : "h-4 w-4"} />
                  </button>
                  <button
                    type="button"
                    onClick={() => onRemove(photo.id)}
                    className="inline-flex size-8 items-center justify-center rounded-lg border border-border text-muted-foreground hover:bg-rose-50 hover:text-rose-700"
                    aria-label="Xóa ảnh"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}
