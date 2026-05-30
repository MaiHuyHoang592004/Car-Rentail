type ChoiceOption = {
  value: string;
  label: string;
  hint?: string;
};

export const VEHICLE_CATEGORY_OPTIONS: ChoiceOption[] = [
  { value: "SEDAN", label: "Sedan", hint: "Di chuyen gia dinh va do thi" },
  { value: "SUV", label: "SUV", hint: "Gam cao, linh hoat duong dai" },
  { value: "HATCHBACK", label: "Hatchback", hint: "Nho gon, de xoay tro" },
  { value: "PICKUP", label: "Pickup", hint: "Cho hang va da dung" },
  { value: "VAN", label: "Van", hint: "Phuc vu nhom lon hoac hang hoa" },
  { value: "MINIVAN", label: "Minivan", hint: "Gia dinh nhieu cho ngoi" },
  { value: "MPV", label: "MPV", hint: "Da muc dich, thuc dung" },
  { value: "ECONOMY", label: "Economy", hint: "Tiet kiem chi phi" },
  { value: "LUXURY", label: "Luxury", hint: "Trải nghiem cao cap" },
  { value: "SPORTS", label: "Sports", hint: "Hieu nang va phong cach" },
];

export const VEHICLE_TRANSMISSION_OPTIONS: ChoiceOption[] = [
  { value: "AUTO", label: "Tu dong" },
  { value: "MANUAL", label: "So san" },
];

export const VEHICLE_FUEL_OPTIONS: ChoiceOption[] = [
  { value: "PETROL", label: "Xang" },
  { value: "GASOLINE", label: "Xang (Gasoline)" },
  { value: "DIESEL", label: "Dau diesel" },
  { value: "HYBRID", label: "Hybrid" },
  { value: "ELECTRIC", label: "Dien" },
  { value: "EV", label: "EV" },
  { value: "LPG", label: "LPG" },
];

export const VEHICLE_SEAT_OPTIONS: ChoiceOption[] = [
  { value: "2", label: "2 cho" },
  { value: "4", label: "4 cho" },
  { value: "5", label: "5 cho" },
  { value: "7", label: "7 cho" },
  { value: "8", label: "8 cho" },
  { value: "9", label: "9 cho" },
  { value: "16", label: "16 cho" },
];

export const VEHICLE_STATUS_CREATE_OPTIONS: ChoiceOption[] = [
  { value: "DRAFT", label: "Luu nhap", hint: "Xuat hien trong tab Draft" },
  { value: "ACTIVE", label: "Kich hoat", hint: "Xuat hien trong tab Active" },
];

export const VEHICLE_STATUS_OPTIONS: ChoiceOption[] = [
  ...VEHICLE_STATUS_CREATE_OPTIONS,
  { value: "MAINTENANCE", label: "Bao tri" },
  { value: "SUSPENDED", label: "Tam ngung" },
  { value: "ARCHIVED", label: "Luu kho" },
];

export const VEHICLE_CITY_OPTIONS: ChoiceOption[] = [
  { value: "Ho Chi Minh", label: "TP. Ho Chi Minh" },
  { value: "Ha Noi", label: "Ha Noi" },
  { value: "Da Nang", label: "Da Nang" },
  { value: "Hai Phong", label: "Hai Phong" },
  { value: "Can Tho", label: "Can Tho" },
  { value: "Nha Trang", label: "Nha Trang" },
  { value: "Da Lat", label: "Da Lat" },
  { value: "Vung Tau", label: "Vung Tau" },
];

const VEHICLE_MAKE_MODEL_MAP: Record<string, string[]> = {
  Toyota: ["Vios", "Corolla Cross", "Fortuner", "Innova Cross", "Raize"],
  Hyundai: ["Accent", "Creta", "Santa Fe", "Elantra", "Stargazer"],
  Kia: ["Morning", "K3", "Seltos", "Carnival", "Sonet"],
  Mazda: ["Mazda2", "Mazda3", "CX-5", "CX-8", "BT-50"],
  Honda: ["City", "Civic", "CR-V", "BR-V", "HR-V"],
  Ford: ["Ranger", "Everest", "Territory", "Transit"],
  Mitsubishi: ["Xpander", "Attrage", "Triton", "Outlander"],
  VinFast: ["VF 3", "VF 5", "VF 6", "VF 7", "VF 8"],
  "Mercedes-Benz": ["C 200", "E 200", "GLC 200", "V 250"],
  BMW: ["320i", "520i", "X3", "X5"],
};

export const VEHICLE_MAKE_OPTIONS: ChoiceOption[] = Object.keys(VEHICLE_MAKE_MODEL_MAP).map(
  (value) => ({
    value,
    label: value,
  }),
);

export function getVehicleModelOptions(make: string): ChoiceOption[] {
  const models = VEHICLE_MAKE_MODEL_MAP[make] ?? [];
  return models.map((value) => ({ value, label: value }));
}

export function getVehicleYearOptions(): ChoiceOption[] {
  const currentYear = new Date().getFullYear() + 1;
  return Array.from({ length: currentYear - 1994 }, (_, index) => {
    const value = String(currentYear - index);
    return { value, label: value };
  });
}
