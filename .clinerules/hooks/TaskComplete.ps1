# TaskComplete Hook
# Purpose: Enforce completion report format.

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
TASK COMPLETION REQUIREMENT

Before claiming the task is complete, provide this exact report:

## Changed files
- path/to/File.ext — why it changed

## Summary
- What changed
- Why this solves the task

## Tests run
- command
- result

## Not tested
- what was not tested
- why

## Remaining risks
- risk or "None known"

## Suggested commit message
type(scope): concise message

Do not claim success without test/build/manual evidence.
If tests could not be run, state the blocker and provide the exact command the user should run.
'@

    Write-HookResponse -Cancel $false -ContextModification $context -ErrorMessage ""
}
catch {
    Write-HookResponse `
        -Cancel $false `
        -ContextModification "" `
        -ErrorMessage "[TaskComplete] Hook error: $($_.Exception.Message)"
}