# TaskCancel Hook
# Purpose: Safe cancellation behavior.

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
TASK CANCELLED

If this task is resumed later:
- Re-check the current file diff.
- Do not continue from memory only.
- Ask for confirmation if the last approved plan is unclear.
'@

    Write-HookResponse -Cancel $false -ContextModification $context -ErrorMessage ""
}
catch {
    Write-HookResponse `
        -Cancel $false `
        -ContextModification "" `
        -ErrorMessage "[TaskCancel] Hook error: $($_.Exception.Message)"
}