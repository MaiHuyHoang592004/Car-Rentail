# TaskStart Hook
# Purpose: Inject default Car-Rentail workflow rules at the beginning of every Cline task.

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
CAR-RENTAIL WORKFLOW CONTEXT

Default model workflow:
- Plan Mode: GPT-5.5 xhigh.
- Act Mode: DeepSeek V4 Flash.
- Final Review: Codex / GPT-5.4 / GPT-5.3-Codex.

Available MCP servers:
- Context7: use for current framework/library documentation.
- Sequential Thinking: use for complex planning, architecture, security, transaction, cancellation, payment, auth, rate-limit, and unclear bug analysis.
- File System: use only inside the current workspace.
- GitHub: read by default; write actions require explicit user approval.
- Postgrest/Postgres: read-only by default.
- Playwright: only for local/dev browser verification.

Task discipline:
- Start with Plan Mode unless the user explicitly asks to execute an already-approved plan.
- Do not edit files during Plan Mode.
- Before implementation, identify affected files/classes and required test evidence.
- During Act Mode, implement exactly the approved plan.
- Keep changes minimal and reviewable.
- Prefer existing project patterns over new abstractions.
- Do not expand scope without explicit approval.

Hard safety rules:
- Never run git add, git commit, git push, git reset, git clean, git checkout, git switch, git merge, or git rebase without explicit approval.
- Never delete files without explicit approval.
- Never mutate database data without explicit approval.
- Never edit .env, credential files, SSH keys, or files outside this workspace without explicit approval.
- Never use Playwright on real logged-in accounts or production sites without explicit approval.

Completion requirement:
Every task must end with:
1. Changed files
2. What changed
3. Commands/tests run
4. Test results
5. What was not tested and why
6. Remaining risks
7. Suggested commit message
'@

    Write-HookResponse -Cancel $false -ContextModification $context -ErrorMessage ""
}
catch {
    Write-HookResponse `
        -Cancel $false `
        -ContextModification "" `
        -ErrorMessage "[TaskStart] Hook error: $($_.Exception.Message)"
}