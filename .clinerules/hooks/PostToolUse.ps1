# PostToolUse Hook
# Purpose: After tool use, remind agent to inspect diff, run tests, and report evidence.

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

function Test-Pattern {
    param(
        [string]$Text,
        [string]$Pattern
    )

    return [regex]::IsMatch(
        $Text,
        $Pattern,
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
}

try {
    $rawInput = [Console]::In.ReadToEnd()
    $payload = $null

    if (-not [string]::IsNullOrWhiteSpace($rawInput)) {
        $payload = $rawInput | ConvertFrom-Json -ErrorAction Stop
    }

    if ($payload -ne $null) {
        $text = $payload | ConvertTo-Json -Compress -Depth 80
    } else {
        $text = $rawInput
    }

    $contextParts = @()

    if (Test-Pattern -Text $text -Pattern "\b(write_to_file|replace_in_file|edited|modified|created|\.java|\.yml|\.yaml|\.xml|\.properties|\.ts|\.tsx|\.js|\.jsx|\.sql)\b") {
        $contextParts += @'
POST_FILE_CHANGE REMINDER

A file appears to have been created or modified.
Before continuing:
- Re-check scope against the approved plan.
- Inspect the changed area and avoid unrelated edits.
- Prefer running the smallest relevant test first.
- Keep a list of changed files and reasons.
'@
    }

    if (Test-Pattern -Text $text -Pattern "\b(mvn|gradle|npm|pnpm|yarn|test|spec|junit|surefire|failsafe)\b") {
        $contextParts += @'
POST_TEST_COMMAND REMINDER

A build/test-related command appears to have run.
Record:
- Exact command
- Pass/fail result
- Failing test names if any
- First relevant error message
- Next smallest diagnostic step
'@
    }

    if (Test-Pattern -Text $text -Pattern "\b(postgres|postgrest|select|schema|table|database|sql)\b") {
        $contextParts += @'
POST_DB_TOOL_REMINDER

Database access occurred.
Keep DB access read-only unless user explicitly approved mutation.
Record any schema/table assumptions used in the plan or implementation.
'@
    }

    if (Test-Pattern -Text $text -Pattern "\b(playwright|browser|screenshot|click|fill|navigate)\b") {
        $contextParts += @'
POST_BROWSER_TOOL_REMINDER

Browser/Playwright access occurred.
Record:
- URL tested
- User flow tested
- Observed behavior
- Any console/network issue if available
Do not use real logged-in accounts or production sites without approval.
'@
    }

    $context = ($contextParts -join "`n`n")

    Write-HookResponse -Cancel $false -ContextModification $context -ErrorMessage ""
}
catch {
    Write-HookResponse `
        -Cancel $false `
        -ContextModification "" `
        -ErrorMessage "[PostToolUse] Hook error: $($_.Exception.Message)"
}