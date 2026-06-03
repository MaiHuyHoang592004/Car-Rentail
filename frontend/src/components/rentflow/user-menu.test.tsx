import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

const logout = vi.fn();
const logoutAll = vi.fn();

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/features/auth/auth-context", () => ({
  useAuth: () => ({
    status: "authenticated",
    user: {
      id: "u-1",
      email: "user@example.com",
      emailVerified: false,
      roles: ["CUSTOMER"],
      fullName: "User Example",
      phone: null,
      dateOfBirth: null,
      addressLine: null,
      driverVerificationStatus: "NOT_SUBMITTED",
    },
    roles: ["CUSTOMER"],
    hasRole: (role: string) => role === "CUSTOMER",
    login: vi.fn(),
    register: vi.fn(),
    logout,
    logoutAll,
    refresh: vi.fn(),
  }),
}));

import { UserMenu } from "./user-menu";

describe("UserMenu", () => {
  it("renders logout-all action and calls handler", async () => {
    logout.mockReset();
    logoutAll.mockReset();

    render(<UserMenu />);

    await userEvent.click(screen.getByRole("button", { name: /user example/i }));
    await userEvent.click(screen.getByRole("menuitem", { name: "Đăng xuất mọi nơi" }));

    expect(logoutAll).toHaveBeenCalledTimes(1);
    expect(logout).not.toHaveBeenCalled();
  });
});
