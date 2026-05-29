# TaskResume Hook
# Purpose: Restore safe context after task resume.

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

    if (-not [string]::IsNullOrWhiteSpace($rawInput)) {
        $null = $rawInput | ConvertFrom-Json -ErrorAction Stop
    }

    $context = @'
TASK RESUME CONTEXT

This task was resumed.
Before editing anything:
1. Reconstruct the last approved plan.
2. Check current changed files mentally or with git status/diff if available.
3. Do not assume previous state is still valid.
4. Continue with minimal scope.
5. If the approved plan is unclear, ask for confirmation before implementation.

Reminder:
- No git add/commit/push/reset/clean/checkout/switch/rebase/merge without approval.
- No DB mutation without approval.
- No file deletion without approval.
'@

    Write-HookResponse -Cancel $false -ContextModification $context -ErrorMessage ""
}
catch {
    Write-HookResponse `
        -Cancel $false `
        -ContextModification "" `
        -ErrorMessage "[TaskResume] Hook error: $($_.Exception.Message)"
}