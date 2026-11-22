# QingZhang release signing setup for Windows PowerShell (5.1 or later).
# Run ONLY on your own trusted PC. This script contains no signing secrets.
# Files must remain local and must NEVER be committed to GitHub.
param(
    [string]$KeystorePath = (Join-Path $PSScriptRoot 'renovation-ledger-release.keystore'),
    [string]$RecoveryPath = (Join-Path $PSScriptRoot 'qingzhang-signing-recovery.txt'),
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = 'a1070503737-dev/QingZhang'
$requiredNames = @(
    'RENOVATION_LEDGER_KEYSTORE_BASE64',
    'RENOVATION_LEDGER_STORE_PASSWORD',
    'RENOVATION_LEDGER_KEY_PASSWORD'
)

function Get-RecoveryValue([string]$content, [string]$label) {
    $escaped = [regex]::Escape($label)
    $match = [regex]::Match($content, "(?m)^$escaped[ `t]+([^\r\n]+)")
    if (-not $match.Success) { throw "Missing field: $label in recovery file." }
    return $match.Groups[1].Value.Trim()
}

function Set-PrivateGithubSecret([string]$name, [string]$value, [string]$ghPath) {
    # Pipe the secret to gh via STDIN, not via command-line arguments.
    # No plain-text temporary files, console output, or Git commits.
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $ghPath
    $info.Arguments = "secret set $name -R $repo"
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.CreateNoWindow = $true
    $process = [System.Diagnostics.Process]::Start($info)
    try {
        $process.StandardInput.Write($value)
        $process.StandardInput.Close()
        $null = $process.StandardOutput.ReadToEnd()
        $errorText = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "GitHub secret setup failed for $name (exit $($process.ExitCode)). $errorText"
        }
        Write-Host "Secret configured: $name"
    } finally {
        $process.Dispose()
    }
}

if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw 'Keystore not found. Put renovation-ledger-release.keystore beside this script.'
}
if (-not (Test-Path -LiteralPath $RecoveryPath -PathType Leaf)) {
    throw 'Recovery text not found. Put qingzhang-signing-recovery.txt beside this script.'
}
$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    throw 'GitHub CLI is missing. Install: winget install --id GitHub.cli ; then run gh auth login.'
}
& gh auth status
if ($LASTEXITCODE -ne 0) {
    throw 'GitHub CLI is not authenticated. Run gh auth login before retrying.'
}

$recoveryText = [System.IO.File]::ReadAllText($RecoveryPath, [System.Text.Encoding]::UTF8)
$storePassword = Get-RecoveryValue $recoveryText '密钥库密码'
$keyPassword = Get-RecoveryValue $recoveryText '密钥密码'
if ([string]::IsNullOrWhiteSpace($storePassword) -or [string]::IsNullOrWhiteSpace($keyPassword)) {
    throw 'Signing passwords are missing.'
}
$keystoreBytes = [System.IO.File]::ReadAllBytes($KeystorePath)
if ($keystoreBytes.Length -lt 1000) { throw 'Keystore file is unexpectedly small.' }
$keystoreBase64 = [Convert]::ToBase64String($keystoreBytes)

Write-Host "Sending three encrypted Actions secrets to $repo ..."
Set-PrivateGithubSecret $requiredNames[0] $keystoreBase64 $gh.Source
Set-PrivateGithubSecret $requiredNames[1] $storePassword $gh.Source
Set-PrivateGithubSecret $requiredNames[2] $keyPassword $gh.Source
[Array]::Clear($keystoreBytes, 0, $keystoreBytes.Length)
$keystoreBase64 = $null
$storePassword = $null
$keyPassword = $null
$recoveryText = $null

if (-not $SkipBuild) {
    Write-Host 'Starting the official 1.3.24 release pipeline...'
    & gh workflow run build-1.3.24.yml -R $repo --ref main
    if ($LASTEXITCODE -ne 0) { throw 'Secrets were configured, but workflow dispatch failed. Run the workflow manually.' }
    Write-Host "Open https://github.com/$repo/actions/workflows/build-1.3.24.yml to verify the result."
} else {
    Write-Host 'Secrets configured. The build was not started because -SkipBuild was specified.'
}
