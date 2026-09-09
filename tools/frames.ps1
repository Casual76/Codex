<#
.SYNOPSIS
  Registra lo schermo del telefono e ne estrae i fotogrammi, per giudicare le animazioni dei sigilli
  dai frame e non "ragionando" (regola dell'engine: references/regole.md, "Tre trappole").

.DESCRIPTION
  1. adb screenrecord per N secondi (tu fai il gesto sul telefono);
  2. scarica il video in tools\frames-out\<nome>\;
  3. se ffmpeg e' nel PATH: estrae i fotogrammi con -fps_mode passthrough (mai fps=30, che riordina
     e duplica su un video a frame rate variabile) e stampa la luminanza media per frame
     (signalstats): un lampo e' un picco isolato in una serie che dovrebbe essere monotona.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\frames.ps1 -Name roccia -Seconds 4
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Name,
  [int]$Seconds = 4
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root "tools\frames-out\$Name"
New-Item -ItemType Directory -Force -Path $out | Out-Null

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Host "adb non trovato." -ForegroundColor Red; exit 1 }

Write-Host "Registro $Seconds s: fai il gesto sul telefono..." -ForegroundColor Cyan
& $adb shell screenrecord --time-limit $Seconds --bit-rate 20000000 /sdcard/codex-frames.mp4
& $adb pull /sdcard/codex-frames.mp4 (Join-Path $out "capture.mp4") | Out-Null
& $adb shell rm /sdcard/codex-frames.mp4

$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if (-not $ffmpeg) {
  Write-Host "Video in $out\capture.mp4. ffmpeg non e' nel PATH: installalo per estrarre i fotogrammi." -ForegroundColor Yellow
  exit 0
}

& ffmpeg -loglevel error -y -i (Join-Path $out "capture.mp4") -fps_mode passthrough (Join-Path $out "frame-%04d.png")
Write-Host "Fotogrammi in $out" -ForegroundColor Green

Write-Host "Luminanza media per fotogramma (YAVG):" -ForegroundColor Cyan
& ffmpeg -loglevel info -i (Join-Path $out "capture.mp4") -vf "signalstats,metadata=print:key=lavfi.signalstats.YAVG" -f null - 2>&1 |
  Select-String "YAVG" | ForEach-Object { ($_ -split "=")[-1].Trim() } | Set-Content (Join-Path $out "yavg.txt")
Get-Content (Join-Path $out "yavg.txt") | Select-Object -First 200
