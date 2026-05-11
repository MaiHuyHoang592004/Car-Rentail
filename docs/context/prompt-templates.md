# Prompt Templates

Copy-paste these templates when working on each task type.

---

## TEMPLATE A — Service method

```
Task: implement [METHOD_NAME] in [SERVICE_CLASS]

Context:
@docs/context/[RELEVANT_CONTEXT_FILE].md

Existing code:
@[entity file]
@[repository file]

Implement:
- Follow the lock order in context exactly
- Throw [EXCEPTION_TYPE]("[ERROR_CODE]") for business rule violations
- Do NOT implement: [list what belongs to later phases]

Output: Java code only, no explanation
```

---

## TEMPLATE B — Controller + DTO

```
Task: implement [HTTP_METHOD] [/api/v1/path] in [CONTROLLER_CLASS]

API contract:
[paste exact section from api-contracts.md]

Rules:
- Required role: [ROLE]
- Ownership check: [booking.customerId == authUserId / etc]
- Do NOT put business logic in controller
- Do NOT use @Transactional in controller
- Return DTO, never entity

Output: controller + DTO classes only
```

---

## TEMPLATE C — Integration test

```
Task: write integration test for [FEATURE]

Extends: BaseIntegrationTest (PostgreSQL Testcontainers)

Scenarios:
1. [happy path description]
2. [conflict case] → expected: 409 [ERROR_CODE]
3. [concurrent case if applicable]

Business rules being tested:
[paste 3-5 bullet points from docs/context/*.md]

Naming: methodName_context_expectedBehavior
Use: MockMvc, @Sql for data setup
```

---

## TEMPLATE D — Lock order verification

```
Review [METHOD_NAME] and answer yes/no for each:
1. Is idempotency key locked BEFORE booking row?
2. Is booking row locked BEFORE availability rows?
3. Are availability rows locked ORDER BY available_date ASC?
4. Is there any DB write happening after the final commit point?
5. Does DataIntegrityViolationException get caught and mapped to 409?

If any answer is no, show the fix.
```
