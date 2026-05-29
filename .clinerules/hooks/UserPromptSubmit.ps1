# UserPromptSubmit Hook
# Purpose: Classify request type and force safe Plan/Act discipline.

$ErrorActionPreference = "Stop"

function Write-HookResponse {
    param(
        [bool]$Cancel = $false,
        [string]$ContextModification = "",
        [string]$ErrorMessage = ""
    )

    [ordered]@{
        cancel = $Cancel
        contextModification = $ContextModification
        errorMessage = $ErrorMessage
    } | ConvertTo-Json -Compress -Depth 20

    exit 0
}

try {
    $rawInput = [Console]::In.ReadToEnd()
    $payload = $null

    if (-not [string]::IsNullOrWhiteSpace($rawInput)) {
        $payload = $rawInput | ConvertFrom-Json -ErrorAction Stop
    }

    $payloadText = ""
    if ($payload -ne $null) {
        $payloadText = ($payload | ConvertTo-Json -Compress -Depth 50)
    } else {
        $payloadText = $rawInput
    }

    $lower = $payloadText.ToLowerInvariant()

    $context = @'
USER PROMPT ROUTING RULES

Before doing anything, classify the request as exactly one:
- PLAN_ONLY
- ACT_FROM_APPROVED_PLAN
- REVIEW_ONLY
- DEBUG
- RESEARCH
- SMALL_ROUTINE_CHANGE

Default behavior:
- If implementation is requested but there is no approved plan, start with Plan Mode first.
- If user explicitly says "act", "execute", "implement this approved plan", then execute only that approved plan.
- If the request is ambiguous, ask one short clarification before editing files.

Output expectations:
- For PLAN_ONLY: do not edit files.
- For ACT_FROM_APPROVED_PLAN: implement only the approved plan.
- For REVIEW_ONLY: do not edit files; report issues.
- For DEBUG: identify reproduction path, likely root cause, minimal fix, and tests.
'@

    $riskKeywords = @(
        "auth",
        "security",
        "jwt",
        "cors",
        "rate limit",
        "rate-limit",
        "payment",
        "refund",
        "cancel",
        "cancellation",
        "transaction",
        "concurrency",
        "role",
        "permission",
        "admin",
        "password",
        "login",
        "register",
        "oauth",
        "google login"
    )

    $isRiskSensitive = $false
    foreach ($kw in $riskKeywords) {
        if ($lower.Contains($kw)) {
            $isRiskSensitive = $true
            break
        }
    }

    if ($isRiskSensitive) {
        $context += @'

RISK-SENSITIVE REQUEST DETECTED

Use Sequential Thinking before implementation.
The plan must include:
1. Security assumptions
2. Edge cases
3. Regression risks
4. Transaction/data consistency risks if applicable
5. Required tests
6. Manual verification steps if automated tests are insufficient

Do not implement until the plan is clear.
'@
    }

    Write-HookResponse -Cancel $false -ContextModification $context -ErrorMessage ""
}
catch {
    Write-HookResponse `
        -Cancel $false `
        -ContextModification "" `
        -ErrorMessage "[UserPromptSubmit] Hook error: $($_.Exception.Message)"
}