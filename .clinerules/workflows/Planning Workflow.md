# Feature Planning Workflow

Goal:
Create a senior-level implementation strategy before coding.

IMPORTANT:
Do not implement code during this workflow.

---

## STEP 1 — Understand Request

Restate:

- business goal
- constraints
- assumptions

If requirements are unclear:

Ask concise clarification questions.

---

## STEP 2 — Use solution-architect

Generate:

# Approach A

- architecture
- complexity
- scalability
- maintainability
- risks

# Approach B

- architecture
- complexity
- scalability
- maintainability
- risks

If useful:

# Approach C

---

## STEP 3 — Use challenge-assumptions

For each approach:

List:

- assumptions
- risks if assumptions are wrong
- validation strategy

---

## STEP 4 — Use plan-reviewer

Review approaches.

Check:

- hidden complexity
- architecture violations
- migration risks
- testing gaps
- scalability concerns

---

## STEP 5 — Recommend Direction

Output:

# Recommended Approach

Explain:

- why selected
- why alternatives rejected
- tradeoffs accepted

---

## STEP 6 — Ask Approval

Ask:

Should we:

1. Proceed with recommended approach
2. Choose another approach
3. Modify the plan

STOP.

Wait for approval.

---

## STEP 7 — Use backend-architect

After approval:

Generate:

# Current Flow

# Affected Files

# Risks

# Edge Cases

# Test Strategy

---

## STEP 8 — Use task-decomposer

Break implementation into slices.

For each slice:

- objective
- files
- implementation steps
- tests
- definition of done

---

## STEP 9 — Final Planning Output

Output:

# Approved Direction

# Slice Plan

# Risks

# Test Evidence Required

STOP.

Do not implement.