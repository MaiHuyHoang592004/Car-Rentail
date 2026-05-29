# Debug Investigation Workflow

Goal:
Find root cause before fixing.

Never start with implementation.

---

## STEP 1

Use debug-specialist.

Collect:

- error
- stacktrace
- logs
- failing test
- reproduction path

---

## STEP 2

Generate hypotheses.

For each hypothesis:

- supporting evidence
- contradicting evidence

Reject weak hypotheses.

---

## STEP 3

Use challenge-assumptions.

Challenge:

- environment assumptions
- DB assumptions
- auth assumptions
- API assumptions

---

## STEP 4

Identify:

# Most Likely Root Cause

Explain evidence.

---

## STEP 5

Generate:

# Smallest Fix

# Safer Alternative

# Risks

---

## STEP 6

Generate:

# Validation Strategy

Include:

- tests
- manual verification
- regression checks

STOP.

Wait for approval before implementation.