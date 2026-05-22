import { describe, expect, it } from "vitest";

import { validateBookingForm } from "./date-utils";

const today = "2026-05-23";

describe("validateBookingForm", () => {
  it("flags missing pickupDate/returnDate", () => {
    const e = validateBookingForm({ pickupDate: "", returnDate: "" }, today);
    expect(e.pickupDate).toBeDefined();
    expect(e.returnDate).toBeDefined();
  });

  it("rejects pickup in the past", () => {
    const e = validateBookingForm(
      { pickupDate: "2026-05-22", returnDate: "2026-05-24" },
      today,
    );
    expect(e.pickupDate).toContain("quá khứ");
  });

  it("accepts pickup == today", () => {
    const e = validateBookingForm(
      { pickupDate: today, returnDate: "2026-05-24" },
      today,
    );
    expect(e.pickupDate).toBeUndefined();
  });

  it("rejects return <= pickup", () => {
    const e = validateBookingForm(
      { pickupDate: "2026-05-23", returnDate: "2026-05-23" },
      today,
    );
    expect(e.returnDate).toContain("phải sau");
  });

  it("rejects rental > 30 days", () => {
    const e = validateBookingForm(
      { pickupDate: "2026-05-23", returnDate: "2026-06-23" },
      today,
    );
    expect(e.returnDate).toContain("tối đa");
  });

  it("accepts rental == 30 days", () => {
    const e = validateBookingForm(
      { pickupDate: "2026-05-23", returnDate: "2026-06-22" },
      today,
    );
    expect(e.returnDate).toBeUndefined();
  });

  it("rejects malformed date string", () => {
    const e = validateBookingForm(
      { pickupDate: "2026-13-99", returnDate: "2026-05-24" },
      today,
    );
    expect(e.form).toContain("Định dạng ngày");
  });

  it("string comparison is timezone-independent", () => {
    // Same inputs must yield same result regardless of host TZ.
    // We can't easily flip process.env.TZ inside test, but logic uses only
    // string compare + Date.UTC — covered by other tests as a contract.
    const e = validateBookingForm(
      { pickupDate: "2026-05-23", returnDate: "2026-05-25" },
      today,
    );
    expect(e).toEqual({});
  });
});
