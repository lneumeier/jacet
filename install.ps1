#Requires -Version 5.1
<#
.SYNOPSIS
  Jacet installer for Windows.

.DESCRIPTION
  Downloads the jacet native binary from a GitHub release, verifies its
  SHA256 checksum, installs it to $env:USERPROFILE\.jacet\bin (or
  $env:JACET_INSTALL_DIR if set), and adds that directory to the current
  user's PATH.

  Do not run multiple instances concurrently against the same $InstallDir —
  the download/move is not protected by a lock.

.EXAMPLE
  irm https://raw.githubusercontent.com/lneumeier/jacet/main/install.ps1 | iex

.EXAMPLE
  & ([scriptblock]::Create((irm https://raw.githubusercontent.com/lneumeier/jacet/main/install.ps1))) v0.1.0
#>
[CmdletBinding()]
param(
  [string] $Version = 'latest'
)

$ErrorActionPreference = 'Stop'

$Repo = 'lneumeier/jacet'
$InstallDir = if ($env:JACET_INSTALL_DIR) { $env:JACET_INSTALL_DIR } else { "$env:USERPROFILE\.jacet\bin" }
$Headers = @{ 'User-Agent' = 'jacet-installer' }

# Only amd64 Windows binaries are published; Windows ARM64 runs them via emulation.
$asset = 'jacet-windows-amd64.exe'

if ($Version -eq 'latest') {
  $release = Invoke-RestMethod -UseBasicParsing -Headers $Headers "https://api.github.com/repos/$Repo/releases/latest"
  $Version = $release.tag_name
  if (-not $Version) {
    throw "Failed to resolve latest release"
  }
}

$base = "https://github.com/$Repo/releases/download/$Version"
$target = Join-Path $InstallDir 'jacet.exe'

Write-Host "Installing jacet $Version (windows-amd64) to $InstallDir"
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null

# Temp path inside $InstallDir so the final Move-Item is same-volume (atomic
# rename on NTFS) and any leftover on crash stays next to the install, not in
# $env:TEMP.
$tmp = Join-Path $InstallDir ('jacet.exe.' + [System.Guid]::NewGuid().ToString('N') + '.download')

$ProgressPreference = 'SilentlyContinue'
try {
  try {
    Invoke-WebRequest -Uri "$base/$asset" -OutFile $tmp -UseBasicParsing -Headers $Headers
  } catch {
    throw "Download failed: $base/$asset`n$($_.Exception.Message)"
  }

  try {
    $checksumContent = (Invoke-WebRequest -Uri "$base/$asset.sha256" -UseBasicParsing -Headers $Headers).Content
  } catch {
    throw "Failed to fetch checksum: $base/$asset.sha256`n$($_.Exception.Message)"
  }

  # Depending on the served Content-Type, Invoke-WebRequest exposes .Content as a
  # Byte[] rather than a string. Decode bytes as UTF-8 before parsing, otherwise the
  # -split / checksum comparison runs against a byte array and always mismatches.
  $checksumLine = if ($checksumContent -is [byte[]]) {
    [System.Text.Encoding]::UTF8.GetString($checksumContent)
  } else {
    $checksumContent
  }

  $expected = ($checksumLine -split '\s+')[0].ToLower()
  if (-not $expected) { throw "Empty checksum file" }

  $actual = (Get-FileHash -Path $tmp -Algorithm SHA256).Hash.ToLower()
  if ($expected -ne $actual) {
    throw "Checksum mismatch: expected $expected, got $actual"
  }

  Move-Item -Force -Path $tmp -Destination $target
} finally {
  if (Test-Path $tmp) { Remove-Item -Force $tmp }
}

Write-Host "Installed: $target"

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')

# Normalize trailing slashes so "C:\Tools\jacet\" and "C:\Tools\jacet" compare equal
# — otherwise we'd append a duplicate PATH entry on every install.
$normalized = $InstallDir.TrimEnd('\', '/')
$existingNormalized = if ($userPath) {
  $userPath.Split(';') | ForEach-Object { $_.TrimEnd('\', '/') }
} else {
  @()
}
$onPath = $existingNormalized -contains $normalized

if (-not $onPath) {
  $newPath = if ($userPath) { "$userPath;$InstallDir" } else { $InstallDir }
  [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
  Write-Host ""
  Write-Host "Added $InstallDir to your user PATH."
  Write-Host "Restart your shell, or run this to use jacet in the current session:"
  Write-Host "  `$env:Path = [Environment]::GetEnvironmentVariable('Path', 'User')"
}
