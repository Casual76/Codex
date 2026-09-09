<#
.SYNOPSIS
  Compila la build di debug di Codex e, se c'e' un dispositivo collegato, la installa e la avvia.

.DESCRIPTION
  Le cartelle di build stanno fuori dal progetto (vedi build.gradle.kts): questo script legge dove
  da local.properties invece di cercarle in app\build.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\build-debug.ps1
  powershell -ExecutionPolicy Bypass -File tools\build-debug.ps1 -NoInstall
#>
[CmdletBinding()]
param(
  [switch]$NoInstall,
  [switch]$Tests
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Get-BuildRoot {
  $localProperties = Join-Path $root "local.properties"
  if (Test-Path $localProperties) {
    $line = Get-Content $localProperties | Where-Object { $_ -match '^codex\.buildDir=' } | Select-Object -First 1
    if ($line) { return ($line -replace '^codex\.buildDir=', '').Trim() }
  }
  return (Join-Path $env:TEMP "codex-build")
}

$tasks = @(":app:assembleDebug")
if ($Tests) { $tasks += "testDebugUnitTest" }

Write-Host "gradlew $($tasks -join ' ')" -ForegroundColor Cyan
& .\gradlew.bat --console=plain @tasks
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = Join-Path (Get-BuildRoot) "app\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { Write-Host "APK non trovato: $apk" -ForegroundColor Red; exit 1 }
Write-Host "APK: $apk ($([math]::Round((Get-Item $apk).Length / 1MB, 1)) MB)" -ForegroundColor Green

if ($NoInstall) { exit 0 }

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Host "adb non trovato, salto l'installazione." -ForegroundColor Yellow; exit 0 }

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" }
if (-not $devices) { Write-Host "Nessun dispositivo collegato, salto l'installazione." -ForegroundColor Yellow; exit 0 }

& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb shell am start -n dev.pampa.codex.debug/dev.pampa.codex.MainActivity | Out-Null
Write-Host "Installata e avviata." -ForegroundColor Green
