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
