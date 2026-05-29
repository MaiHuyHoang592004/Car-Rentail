# Release Gate Evidence (2026-05-29)

## Branch under validation

- `codex/release-hardening-gate`

## Commands executed

1. `.\mvnw.cmd -q test`
2. `.\mvnw.cmd -q verify -Pintegration-tests`

Executed at: `2026-05-29 15:47:15 +07:00`

## Results

- Surefire summary: `tests=450 failures=0 errors=0 skipped=0`
- Failsafe summary: `completed=155 failures=0 errors=0 skipped=0`
- Integration DB stability: no `FATAL: sorry, too many clients already` observed in this gate run.

## Commit map (stacked PR packaging)

- PR-A (`hardening(security)`): `2f3be84`
- PR-B (`test(integration)`): `e4e7f5a`
- PR-C (`feat(auth)`): `bdc4536`
- PR-D (`feat(api)`): `eb14ee5`

## Key regression intents covered by gate

- Invalid enum filters return `400 VALIDATION_ERROR`.
- Auth/security/rate-limit integration suite still green.
- Booking/payment cancellation policy integration suite still green.
- Containerized integration suite runs without DB connection exhaustion.

---

# Frontend Evidence Addendum (2026-05-30)

## Scope under validation

- Customer payment page: `/bookings/{id}/payment`
- Admin pages: `/admin`, `/admin/listings`, `/admin/listings/{id}`, `/admin/users`
- Frontend production type/build gate after the admin/payment UX slice

## Commands executed

1. `cd frontend && npm test`
2. `cd frontend && npm run build`

Executed at: `2026-05-30 +07:00`

## Results

- Vitest summary: `21` test files, `125` tests passed, `0` failed
- Next.js production build: passed

## Browser smoke coverage

Executed with a real headless browser against local `localhost:3000` using mocked auth/API responses because backend infra was not running in this evidence session.

Validated:

- `/bookings/:id/payment` renders bank selection and reaches authorized state
- `/admin` renders dashboard
- `/admin/listings` renders queue
- `/admin/listings/:id` renders detail and approve flow remains healthy
- `/admin/users` renders filtered user table

## Limits of this addendum

- No backend Maven tests were rerun in this session
- No full end-to-end backend-integrated browser smoke was possible because local Docker/PostgreSQL/Redis/backend were unavailable
