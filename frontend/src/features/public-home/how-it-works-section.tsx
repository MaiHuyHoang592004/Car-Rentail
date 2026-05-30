import { CalendarCheck, KeyRound, Search } from "lucide-react";

const STEPS = [
  {
    icon: Search,
    title: "Tìm xe ưng ý",
    description: "Duyệt xe theo địa điểm, thời gian, dòng xe và ngân sách phù hợp.",
  },
  {
    icon: CalendarCheck,
    title: "Đặt xe và giữ chỗ",
    description: "Chọn lịch thuê, kiểm tra chi phí rõ ràng và giữ chỗ trước chuyến đi.",
  },
  {
    icon: KeyRound,
    title: "Nhận xe và khởi hành",
    description: "Hoàn tất thủ tục với chủ xe tại điểm hẹn rồi bắt đầu hành trình.",
  },
];

export function HowItWorksSection() {
  return (
    <section className="py-16 md:py-20">
      <div className="mb-10 text-center">
        <h2 className="text-3xl font-bold text-foreground">Bắt đầu hành trình chỉ với 3 bước</h2>
        <p className="mt-2 text-base text-muted-foreground">
          Dễ dàng, minh bạch và an tâm trong từng thao tác.
        </p>
      </div>

      <div className="grid gap-8 md:grid-cols-3">
        {STEPS.map((step) => {
          const Icon = step.icon;
          return (
            <div key={step.title} className="flex flex-col items-center text-center">
              <div className="mb-4 flex size-16 items-center justify-center rounded-full bg-[#dbe1ff] text-primary transition-colors">
                <Icon className="h-7 w-7" strokeWidth={1.7} />
              </div>
              <h3 className="text-xl font-semibold text-foreground">{step.title}</h3>
              <p className="mt-2 max-w-sm text-sm leading-6 text-muted-foreground">{step.description}</p>
            </div>
          );
        })}
      </div>
    </section>
  );
}
