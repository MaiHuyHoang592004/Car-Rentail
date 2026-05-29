import { Car, CreditCard, Key, CalendarCheck } from "lucide-react";

const STEPS = [
  {
    icon: Car,
    title: "Chon xe",
    description: "Duyet xe theo thanh pho, ngay va ngan sach phu hop voi ban.",
  },
  {
    icon: CalendarCheck,
    title: "Giu xe",
    description: "Dat coc giu cho de xe duoc bao hanh trong vai phut.",
  },
  {
    icon: CreditCard,
    title: "Thanh toan",
    description: "Thanh toan an toan qua nen tang tich hop. Khong phi an.",
  },
  {
    icon: Key,
    title: "Nhan xe",
    description: "Nhan xe truc tiep voi chu xe. Kiem tra va bat dau hanh trinh.",
  },
];

export function HowItWorksSection() {
  return (
    <section className="rounded-xl border border-border bg-card p-6">
      <div className="mb-6">
        <h2 className="text-xl font-bold text-foreground">Cach thuc hoat dong</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Thue xe chi trong 4 buoc don gian.
        </p>
      </div>
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {STEPS.map((step, index) => {
          const Icon = step.icon;
          return (
            <div key={step.title} className="flex flex-col items-start gap-3">
              <div className="flex items-center gap-3">
                <span className="flex size-9 items-center justify-center rounded-full bg-primary/10">
                  <Icon className="size-4 text-primary" strokeWidth={1.5} />
                </span>
                <span className="font-heading text-sm font-semibold text-muted-foreground">
                  {String(index + 1).padStart(2, "0")}
                </span>
              </div>
              <div>
                <h3 className="font-semibold text-foreground">{step.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{step.description}</p>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}