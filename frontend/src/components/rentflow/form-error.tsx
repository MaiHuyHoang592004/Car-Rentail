import type { ReactNode } from "react";

type FormErrorProps = {
  children: ReactNode;
  title?: string;
};

export function FormError({ children, title }: FormErrorProps) {
  return (
    <section
      role="alert"
      className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"
    >
      {title ? <p className="font-semibold text-rose-900">{title}</p> : null}
      <div className={title ? "mt-1" : undefined}>{children}</div>
    </section>
  );
}
