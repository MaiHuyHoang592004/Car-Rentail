# RentFlow Screen Inventory (Phase 5)

This document provides a comprehensive map of all screens, modals, and states currently designed for the RentFlow platform, categorized by user role and implementation phase.

## 1. Guest / Public Flow
| Route | Screen Name | Role | Status | Primary Actions |
| :--- | :--- | :--- | :--- | :--- |
| `/` | Landing Page | Guest | Phase 5 Active | Search, View Featured Fleet, Register |
| `/listings` | Search Results | Guest | Phase 5 Active | Filter, View Listing Detail |
| `/listings/:id` | Listing Detail | Guest | Phase 5 Active | Check Availability, Login to Book |
| `/login` | Login | Guest | Phase 5 Active | Authenticate |
| `/register` | Create Account | Guest | Phase 5 Active | Register (Customer/Host) |

## 2. Customer Flow
| Route | Screen Name | Role | Status | Primary Actions |
| :--- | :--- | :--- | :--- | :--- |
| `/listings/:id/book` | Create Booking (Hold) | Customer | Phase 5 Active | Select Dates, Set Locations, Hold Car |
| `/me/profile` | Profile Settings | Customer | Phase 5 Active | Update Personal Info, View Role/Status |
| `/me/bookings` | My Bookings List | Customer | Phase 5 Active | Filter by Status, View Detail |
| `/bookings/:id` | Booking Detail (HELD) | Customer | Phase 5 Active | Edit Locations, Cancel Hold |
| `modal` | Edit Locations | Customer | Phase 5 Active | Update Pickup/Return Notes |
| `modal` | Cancel Booking | Customer | Phase 5 Active | Confirm Cancellation with Reason |

## 3. Host Flow
| Route | Screen Name | Role | Status | Primary Actions |
| :--- | :--- | :--- | :--- | :--- |
| `/host/dashboard` | Host Overview | Host | Phase 5 Active | View Stats, Quick Tasks |
| `/host/vehicles` | Fleet Management | Host | Phase 5 Active | Add Vehicle, Edit, Archive |
| `/host/vehicles/new` | Add New Vehicle | Host | Phase 5 Active | Input Specs, Save Vehicle |
| `/host/listings` | Listing Management | Host | Phase 5 Active | Create Listing, Submit for Approval |
| `/host/listings/:id/availability` | Availability Manager | Host | Phase 5 Active | Block/Unblock Dates |
| `/host/listings/:id` | Listing Preview | Host | Phase 5 Active | Edit, Submit, Archive |

## 4. Admin Flow
| Route | Screen Name | Role | Status | Primary Actions |
| :--- | :--- | :--- | :--- | :--- |
| `/admin` | System Overview | Admin | Phase 5 Active | View Platform Stats, Task Queue |
| `/admin/listings` | Listing Approval Queue | Admin | Phase 5 Active | Review Pending Listings |
| `/admin/listings/:id` | Listing Review Detail | Admin | Phase 5 Active | Approve, Reject, Suspend |
| `/admin/users` | User Management | Admin | Phase 5 Active | List Users, View Profiles |
| `modal` | Reject Listing | Admin | Phase 5 Active | Submit Rejection Reason |
| `modal` | Suspend Listing | Admin | Phase 5 Active | Submit Suspension Reason |

## 5. System States (Global)
| Scenario | Screen/State Name | Role | Status | Primary Actions |
| :--- | :--- | :--- | :--- | :--- |
| `404 / 410` | Resource Not Found | All | Phase 5 Active | Back to Listings, Go Home |
| `403` | Access Denied | All | Phase 5 Active | Contact Support, Return to Dashboard |
| `Skeleton` | Loading Skeletons | All | Phase 5 Active | Visual feedback during data fetch |
| `Empty` | Empty State Variants | All | Phase 5 Active | Prompt to Add Data (e.g., "Add Vehicle") |

## 6. Future Scope (Phase 6+)
| Feature | Screen Name | Role | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| Payment | Checkout / Payment | Customer | Future | Waiting for payment gateway |
| Verification | Driver Verification | Customer | Future | Waiting for document API |
| Audit | System Audit Logs | Admin | Future | backend preparation only |
| Photos | Listing Photo Manager | Host | Future | Waiting for file upload API |
| Reports | Earnings & Reports | Host/Admin | Future | Advanced analytics phase |
