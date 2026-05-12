---
name: RentFlow Design System
colors:
  surface: '#faf8ff'
  surface-dim: '#d9d9e4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3fe'
  surface-container: '#ededf8'
  surface-container-high: '#e7e7f3'
  surface-container-highest: '#e2e1ed'
  on-surface: '#191b23'
  on-surface-variant: '#434654'
  inverse-surface: '#2e3039'
  inverse-on-surface: '#f0f0fb'
  outline: '#737686'
  outline-variant: '#c3c5d7'
  surface-tint: '#1353d8'
  primary: '#003fb1'
  on-primary: '#ffffff'
  primary-container: '#1a56db'
  on-primary-container: '#d4dcff'
  inverse-primary: '#b5c4ff'
  secondary: '#9d4300'
  on-secondary: '#ffffff'
  secondary-container: '#fd761a'
  on-secondary-container: '#5c2400'
  tertiary: '#852b00'
  on-tertiary: '#ffffff'
  tertiary-container: '#ad3b00'
  on-tertiary-container: '#ffd4c5'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dbe1ff'
  primary-fixed-dim: '#b5c4ff'
  on-primary-fixed: '#00174d'
  on-primary-fixed-variant: '#003dab'
  secondary-fixed: '#ffdbca'
  secondary-fixed-dim: '#ffb690'
  on-secondary-fixed: '#341100'
  on-secondary-fixed-variant: '#783200'
  tertiary-fixed: '#ffdbcf'
  tertiary-fixed-dim: '#ffb59a'
  on-tertiary-fixed: '#380d00'
  on-tertiary-fixed-variant: '#802a00'
  background: '#faf8ff'
  on-background: '#191b23'
  surface-variant: '#e2e1ed'
typography:
  display:
    fontFamily: Manrope
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '700'
    lineHeight: '1.2'
  headline-md:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
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
  label-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: '1.2'
    letterSpacing: 0.05em
  caption:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: '1.4'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  container_max_width: 1280px
  gutter: 24px
  margin_mobile: 16px
  stack_sm: 8px
  stack_md: 16px
  stack_lg: 32px
---

## Brand & Style

This design system is engineered for a professional car rental marketplace where trust and efficiency are paramount. The visual language balances corporate reliability with a high-performance, modern tech aesthetic. 

The style is **Minimalist / Corporate Modern**, prioritizing clarity and speed of task completion. It utilizes generous whitespace to reduce cognitive load during the booking process, while subtle depth markers (soft shadows and tonal layering) provide a tactile sense of interaction. The overall feel is "premium utility"—every element serves a functional purpose while maintaining a sophisticated, streamlined appearance.

## Colors

The palette is anchored by **RentFlow Blue**, a deep, saturated sapphire that evokes the reliability of traditional logistics brands but with a digital-first vibrance. **Accent Orange** is used exclusively for primary calls to action (CTAs) and critical interactions, ensuring a clear path to conversion.

The neutral palette leverages "Cool Grays" (Slates) to maintain a crisp, professional atmosphere. Surfaces are predominantly white or very light gray to maximize the impact of data and imagery. Status colors are distinct and high-contrast, ensuring that the state of a reservation (HELD, ACTIVE, etc.) is immediately recognizable.

## Typography

This design system employs a dual-font strategy. **Manrope** is used for headlines to provide a refined, modern character with its geometric yet warm letterforms. **Inter** is utilized for all body copy and UI labels to ensure maximum readability at small sizes, particularly in data-heavy car specification lists.

Hierarchy is established through clear weight contrasts rather than excessive size variations. Bold weights are reserved for car titles and pricing, while tabular data uses the regular weight of Inter for a clean, non-distracting flow.

## Layout & Spacing

The design system follows a **Fixed Grid** model for desktop environments, centering content within a 1280px container to maintain focus. On mobile, it transitions to a fluid single-column layout with 16px side margins.

A 4px baseline grid ensures vertical rhythm. Car listing grids utilize a 12-column structure:
- **Desktop:** 3 columns (4-span per card) or 4 columns (3-span per card).
- **Tablet:** 2 columns (6-span per card).
- **Mobile:** 1 column (full span).

Spacing between related elements (e.g., car title and price) uses `stack_sm`, while spacing between independent sections uses `stack_lg`.

## Elevation & Depth

Visual hierarchy is achieved through **Ambient Shadows** and **Tonal Layers**. 

1.  **Level 0 (Base):** Background color (`#F8FAFC`).
2.  **Level 1 (Cards/Surface):** White surfaces with a subtle 1px border (`#E2E8F0`) and a soft shadow (0px 4px 6px -1px rgba(0,0,0,0.05)).
3.  **Level 2 (Interaction/Hover):** Enhanced shadow (0px 10px 15px -3px rgba(0,0,0,0.1)) to indicate clickability.
4.  **Level 3 (Modals/Overlays):** Deep, diffused shadows with a semi-transparent backdrop blur (12px) to keep the user focused on the booking flow.

Avoid heavy black shadows; use the primary dark slate color with very low opacity for a more natural, professional feel.

## Shapes

The design system uses a **Rounded** shape language to appear approachable and modern without losing its professional edge. 

- **Standard Elements:** 0.5rem (8px) for buttons, input fields, and small badges.
- **Large Elements:** 1rem (16px) for car listing cards and booking containers.
- **Media:** Images within cards should inherit the card's top roundedness to maintain a cohesive silhouette.

## Components

### Car Listing Cards
Cards must feature a high-quality image at the top, followed by a structured content area. Use `label-md` for the car category (e.g., LUXURY, SUV) and `headline-md` for the car model name. Pricing should be clearly separated using a right-aligned or bottom-aligned bold weight.

### Buttons & Inputs
- **Primary Button:** Solid RentFlow Blue with white text.
- **Action Button:** Solid Accent Orange for "Book Now" or "Proceed."
- **Inputs:** 1px border in light slate. On focus, the border changes to RentFlow Blue with a subtle 2px outer glow.

### Status Badges
Badges use a "soft pill" style: a light tinted background with high-saturation text for contrast.
- **HELD:** Light Indigo background / Indigo text.
- **ACTIVE:** Light Emerald background / Emerald text.
- **PENDING:** Light Amber background / Amber text.

### Booking Calendar
The calendar uses a clean grid without heavy borders. Selected dates are marked with a solid RentFlow Blue circle, while the range between start and end dates is shown with a light blue background tint.

### Filters & Chips
Use chips for quick-filtering car features (e.g., Automatic, Electric, GPS). These should have a light gray border that fills with RentFlow Blue when toggled on.