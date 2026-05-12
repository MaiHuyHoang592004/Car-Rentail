export type MockRole = "GUEST" | "CUSTOMER" | "HOST" | "ADMIN";

export type MockUser = {
  id: string;
  fullName: string;
  email: string;
  roles: MockRole[];
  status: "ACTIVE" | "SUSPENDED";
  driverVerificationStatus: "NOT_SUBMITTED" | "PENDING" | "APPROVED" | "REJECTED";
};

export const MOCK_USERS: MockUser[] = [
  {
    id: "usr-guest",
    fullName: "Guest Visitor",
    email: "guest@rentflow.vn",
    roles: ["GUEST"],
    status: "ACTIVE",
    driverVerificationStatus: "NOT_SUBMITTED",
  },
  {
    id: "usr-customer",
    fullName: "Minh Nguyen",
    email: "minh.nguyen@rentflow.vn",
    roles: ["CUSTOMER"],
    status: "ACTIVE",
    driverVerificationStatus: "APPROVED",
  },
  {
    id: "usr-host",
    fullName: "Lan Tran",
    email: "lan.tran@rentflow.vn",
    roles: ["HOST"],
    status: "ACTIVE",
    driverVerificationStatus: "APPROVED",
  },
  {
    id: "usr-admin",
    fullName: "Admin Operator",
    email: "admin@rentflow.vn",
    roles: ["ADMIN"],
    status: "ACTIVE",
    driverVerificationStatus: "APPROVED",
  },
];
