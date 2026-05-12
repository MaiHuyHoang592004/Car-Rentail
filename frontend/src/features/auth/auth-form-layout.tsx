import type { ReactNode } from "react";

type AuthFormLayoutProps = {
  children: ReactNode;
  footer: ReactNode;
  errorBanner?: ReactNode;
};

export function AuthFormLayout({ children, footer, errorBanner }: AuthFormLayoutProps) {
  return (
    <div className="space-y-4">
      {errorBanner}
      <div className="space-y-3">{children}</div>
      <div className="border-t border-border pt-4 text-sm text-muted-foreground">{footer}</div>
    </div>
  );
}
