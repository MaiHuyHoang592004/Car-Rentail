---
name: RentFlow Design System
colors:
  surface: '#fcf8fa'
  surface-dim: '#dcd9db'
  surface-bright: '#fcf8fa'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3f5'
  surface-container: '#f0edef'
  surface-container-high: '#eae7e9'
  surface-container-highest: '#e4e2e4'
  on-surface: '#1b1b1d'
  on-surface-variant: '#45464d'
  inverse-surface: '#303032'
  inverse-on-surface: '#f3f0f2'
  outline: '#76777d'
  outline-variant: '#c6c6cd'
  surface-tint: '#565e74'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#131b2e'
  on-primary-container: '#7c839b'
  inverse-primary: '#bec6e0'
  secondary: '#0058be'
  on-secondary: '#ffffff'
  secondary-container: '#2170e4'
  on-secondary-container: '#fefcff'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#0b1c30'
  on-tertiary-container: '#75859d'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dae2fd'
  primary-fixed-dim: '#bec6e0'
  on-primary-fixed: '#131b2e'
  on-primary-fixed-variant: '#3f465c'
  secondary-fixed: '#d8e2ff'
  secondary-fixed-dim: '#adc6ff'
  on-secondary-fixed: '#001a42'
  on-secondary-fixed-variant: '#004395'
  tertiary-fixed: '#d3e4fe'
  tertiary-fixed-dim: '#b7c8e1'
  on-tertiary-fixed: '#0b1c30'
  on-tertiary-fixed-variant: '#38485d'
  background: '#fcf8fa'
  on-background: '#1b1b1d'
  surface-variant: '#e4e2e4'
typography:
  display:
    fontFamily: Manrope
    fontSize: 48px
    fontWeight: '800'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  h1:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.01em
  h2:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '700'
    lineHeight: '1.3'
  h3:
    fontFamily: Manrope
    fontSize: 20px
    fontWeight: '600'
    lineHeight: '1.4'
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  label-caps:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '700'
    lineHeight: '1'
    letterSpacing: 0.05em
  chip:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: '1'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  layout_margin: 24px
  layout_gutter: 16px
---

## Brand & Style

This design system is built for a dual-sided automotive ecosystem, balancing the high-utility needs of SaaS fleet management with the frictionless experience of a consumer marketplace. The brand personality is **authoritative, efficient, and reliable**. 

The visual style follows a **Modern Corporate** aesthetic: it utilizes a high-contrast foundation of deep navy and slate to establish trust, punctuated by "Electric Blue" to guide action. The interface prioritizes clarity and data density without feeling cluttered. It employs a "subtle depth" approach—using soft borders and tonal layering rather than heavy shadows—to maintain a clean, professional finish that feels native to high-end automotive software.

## Colors

The palette is anchored by **Deep Navy (#0F172A)**, providing a premium "black-tie" feel for navigation and primary headings. **Electric Blue (#3B82F6)** serves as the functional driver for interactivity, links, and primary calls to action.

### Functional Logic:
- **Surface Strategy:** Use `surface-container` for interactive elements and `background` for the main dashboard background to create subtle separation.
- **Status Semantic:** 
  - **Success (Green):** Confirmed bookings and verified vehicles.
  - **Warning (Amber):** Pending maintenance or expiring documents.
  - **Danger (Red):** Cancelled bookings or suspended listings.
  - **Hold (Purple):** Custom accent for the 15-minute checkout countdown/hold state.

## Typography

This design system uses a dual-font strategy: **Manrope** for headlines to provide a modern, engineered feel, and **Inter** for body text and data-heavy views to ensure maximum legibility.

- **Scale:** Headlines use a heavier weight (700-800) and tighter letter spacing.
- **Micro-copy:** Status chips and labels use `label-caps` for maximum distinction.

## Layout & Spacing

This design system utilizes an **8px linear grid**. All margins, paddings, and height increments are multiples of 8.

### App Shell Layouts:
1. **The Marketplace (Guest/Customer):** A centered **Fixed Grid** (max-width 1280px) with a persistent top-bar.
2. **The Console (Host/Admin):** A **Fluid Grid** with a fixed-width left sidebar (280px).

## Elevation & Depth

We avoid heavy drop shadows. Depth is achieved through:
- **Level 1 (Card):** 1px border (#E2E8F0) and a subtle 2px blur shadow.
- **Level 2 (Hover):** 1px border (#CBD5E1) and a 4px blur shadow.
- **Level 3 (Overlay):** Modals and Toasts with a 12px blur shadow.

## Shapes

The shape language is **Professional Rounded**. 
- **Buttons/Inputs:** 8px radius (`rounded-md`).
- **Vehicle Cards:** 16px (`rounded-lg`) for a consumer-friendly feel.
- **Chips:** `rounded-full` (pill shape).

## Components

### 1. Buttons
- **Primary:** Navy background, white text.
- **Secondary:** Blue background, white text.
- **Destructive:** Red background for critical admin actions (Reject/Suspend).

### 2. Status Chips
Consistent color-coding for backend enums: `ACTIVE` (Green), `DRAFT` (Gray), `PENDING_APPROVAL` (Amber), `SUSPENDED` (Red), `HOLD` (Purple).

### 3. Specialty Components
- **Availability Calendar:** Uses a high-contrast grid with semantic coloring for date states.
- **DataTable:** 1px horizontal dividers with a sticky `surface-container-low` header.
