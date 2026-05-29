# PreCompact Hook
# Purpose: Preserve important state before context compaction.

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
PRE-COMPACTION PRESERVATION RULES

Before compacting conversation context, preserve these details:

1. Current task goal
2. Approved plan
3. Rejected alternatives
4. Affected files/classes
5. Files already changed
6. Commands/tests already run
7. Test results and failures
8. Remaining TODOs
9. User decisions and constraints
10. Safety constraints:
   - no git write/history operations without approval
   - no DB mutation without approval
   - no file deletion without approval
   - no secrets/env access without approval
   - Playwright local/dev only unless approved

Also preserve model workflow:
- Plan Mode: GPT-5.5 xhigh
- Act Mode: DeepSeek V4 Flash
- Final Review: Codex / GPT-5.4 / GPT-5.3-Codex
'@

    Write-HookResponse -Cancel $false -ContextModification $context -ErrorMessage ""
}
catch {
    Write-HookResponse `
        -Cancel $false `
        -ContextModification "" `
        -ErrorMessage "[PreCompact] Hook error: $($_.Exception.Message)"
}