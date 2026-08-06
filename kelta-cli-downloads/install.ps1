# kelta CLI installer (Windows x64).
#   irm https://downloads.kelta.io/cli/install.ps1 | iex
# Optional env: KELTA_INSTALL_DIR (default %LOCALAPPDATA%\kelta\bin), KELTA_DOWNLOADS_URL.
$ErrorActionPreference = "Stop"

$base = if ($env:KELTA_DOWNLOADS_URL) { $env:KELTA_DOWNLOADS_URL } else { "https://downloads.kelta.io" }
$installDir = if ($env:KELTA_INSTALL_DIR) { $env:KELTA_INSTALL_DIR } else { Join-Path $env:LOCALAPPDATA "kelta\bin" }

$version = (Invoke-RestMethod "$base/cli/latest.txt").Trim()
if (-not $version) { throw "Could not read $base/cli/latest.txt" }

$file = "kelta-windows-x64.exe"
$url = "$base/cli/releases/$version/$file"
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) "kelta-install-$version.exe"

Write-Host "Downloading kelta $version (windows-x64)..."
Invoke-WebRequest -Uri $url -OutFile $tmp

$sums = (Invoke-RestMethod "$base/cli/releases/$version/SHA256SUMS") -split "`n"
$expected = ($sums | Where-Object { $_ -match [regex]::Escape($file) }) -split "\s+" | Select-Object -First 1
if (-not $expected) { throw "No checksum for $file in SHA256SUMS" }
$actual = (Get-FileHash -Algorithm SHA256 $tmp).Hash.ToLower()
if ($expected -ne $actual) { throw "Checksum mismatch - aborting" }

New-Item -ItemType Directory -Force -Path $installDir | Out-Null
Move-Item -Force $tmp (Join-Path $installDir "kelta.exe")

Write-Host "kelta $version installed to $installDir\kelta.exe"
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*$installDir*") {
  Write-Host "NOTE: $installDir is not on your PATH. Add it with:"
  Write-Host "  [Environment]::SetEnvironmentVariable('Path', `"$userPath;$installDir`", 'User')"
}
Write-Host "Get started: kelta auth login --url https://api.kelta.io --tenant <slug>"
