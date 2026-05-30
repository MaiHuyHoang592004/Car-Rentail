import Link from "next/link";

const CATEGORIES = [
  {
    label: "Sedan",
    href: "/listings?category=SEDAN",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuCSKxUKTBx3YMQlaLnBgDRH26rxBsrBHiincL-ER4L6n_6rQj1MOHfqMsCBYUDBsKG2jS-oIcLqwD_OxWGmHA-q2FyViPX6R9rfugfYLUS_NE6dS262iiOogTIf57tUeLnT6Z96OVbt34MtxIZ5OoOKIjNbRM_J-mKMtlffOLWJ7Ijdo_lOxdV1dmBOmiXmPdGztehmB730rYAht2-nSr_toZGSjivtXtq0qYR8nroP2rimwN2TzVQZ2QR7_9RdIC7sLcjdO3FKTv0",
  },
  {
    label: "SUV",
    href: "/listings?category=SUV",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuCtH4_g20F6lmBJsdxWYar6GIXj1WTENe5BQcWHGWcRgaT97FsuCebaP1DsYGoQZP2gmYc3vsF4jrfTvzMd09SeiZd6dN3OENBkRcUfc2xdYtTDbsu4PdH_9VjEjPT9CEqIfh0cZOvRVLwHaoXZuRNtRZWdLLJtT3YrGBYvVf1LX1GtmFR46i2duKyjx2A_vWkzy93moqusSJe6wGYti5LXxOufrT5kwXJpBYc4NXD_WyKRALgBuxxB8E2jejWSZ89ou6NhaE1jz-I",
  },
  {
    label: "Hatchback",
    href: "/listings?category=HATCHBACK",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuDk6q_LFj5iaIvQavF6arqMYa_asokF1oAMiBiNDBJR6DreBGjwiZjy3GNu-hpLk9PnNWuhaN49t8GqiFC-KkP6YyThs62fZ1pjT-FN-EklrVeFgIvwA4NU20hRZHLBgndhn1RgVuNr_gW7ah6_fNjWx0NBv1KoEvvSqtwgdeKV8NirDreunkDjgCT2yp0T9JXL3B1PmvpUJhx8R9cuXjwaAJxjSaWx9FVQ7VRApU5baGVw2LE4qw2OyzuQgD5Y0bCXuJT8f0yMW2M",
  },
  {
    label: "Xe điện",
    href: "/listings?fuelType=EV",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuAGUqWgvEb4Z9HU2Uj0S89ATnlIGSfQZpAJLXr-TlJgKD6ee_b_XkcDGhI3CkaZL2erBIf-nDn_BnPIUKjIkMkdqA6MQRJOGoArkuVRCl-H2CbccxzKRph9TbLzaP3-0LPBDxvlLjaywJ2dlmWU9phcOGV5pj4PmYxXdNI4VOKYtXxFulb99DyV1Q-4DHA_zjnAoMnnNoknhFerkWFU0HUvsfg0IAEMY0x7QgJ31oVK0D1SPVHRwE7pG2pZgikD9O6Z0cfZzcQAPrA",
  },
];

export function VehicleCategoriesSection() {
  return (
    <section className="py-16 md:py-20">
      <div className="mb-6">
        <h2 className="text-2xl font-bold text-foreground">Khám phá theo dòng xe</h2>
        <div className="mt-3 h-1 w-12 rounded-full bg-primary" />
      </div>

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {CATEGORIES.map((category) => (
          <Link
            key={category.label}
            href={category.href}
            className="group relative aspect-[4/3] overflow-hidden rounded-[1.5rem] bg-muted"
          >
            <img
              src={category.image}
              alt={category.label}
              className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
            />
            <div className="absolute inset-0 flex items-end bg-gradient-to-t from-black/66 via-black/12 to-transparent p-4">
              <span className="text-sm font-semibold text-white md:text-base">{category.label}</span>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
