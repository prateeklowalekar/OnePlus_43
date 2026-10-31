<#
.SYNOPSIS
    Syncs GBA and NDS ROMs from PC (work\roms) to connected Android TV over ADB.
.PARAMETER Target
    Target TV: 'TCL' (192.168.1.22:5555) or 'OnePlus' (192.168.1.29:5555).
.PARAMETER DeviceIp
    Target TV IP address (defaults based on Target, or manual IP:port).
#>
param(
    [ValidateSet("TCL", "OnePlus")]
    [string]$Target = "TCL",
    [string]$DeviceIp = ""
)

if ([string]::IsNullOrWhiteSpace($DeviceIp)) {
    if ($Target -eq "OnePlus") {
        $DeviceIp = "192.168.1.29:5555"
    } else {
        $DeviceIp = "192.168.1.22:5555"
    }
}

$PSScriptRootLocal = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent $PSScriptRootLocal
$Adb = "adb"

Write-Host "Connecting to TV ($Target) at $DeviceIp..." -ForegroundColor Cyan
& $Adb connect $DeviceIp

Write-Host "Ensuring ROM directories on TV..." -ForegroundColor Cyan
& $Adb -s $DeviceIp shell "mkdir -p /sdcard/ROMs/GBA /sdcard/ROMs/NDS"

# Sync GBA ROMs
$GbaSource = Join-Path $ProjectRoot "work\roms\GBA"
if (Test-Path $GbaSource) {
    $gbaFiles = Get-ChildItem -Path $GbaSource -File | Where-Object { $_.Extension -match '^\.(gba|zip|7z)$' }
    if ($gbaFiles.Count -gt 0) {
        Write-Host "Pushing $($gbaFiles.Count) GBA ROM(s)..." -ForegroundColor Green
        foreach ($f in $gbaFiles) {
            Write-Host " -> $($f.Name)"
            & $Adb -s $DeviceIp push $f.FullName "/sdcard/ROMs/GBA/$($f.Name)"
        }
    } else {
        Write-Host "No GBA files (.gba, .zip) found in work\roms\GBA" -ForegroundColor Yellow
    }
}

# Sync NDS ROMs
$NdsSource = Join-Path $ProjectRoot "work\roms\NDS"
if (Test-Path $NdsSource) {
    $ndsFiles = Get-ChildItem -Path $NdsSource -File | Where-Object { $_.Extension -match '^\.(nds|zip|7z)$' }
    if ($ndsFiles.Count -gt 0) {
        Write-Host "Pushing $($ndsFiles.Count) NDS ROM(s)..." -ForegroundColor Green
        foreach ($f in $ndsFiles) {
            Write-Host " -> $($f.Name)"
            & $Adb -s $DeviceIp push $f.FullName "/sdcard/ROMs/NDS/$($f.Name)"
        }
    } else {
        Write-Host "No NDS files (.nds, .zip) found in work\roms\NDS" -ForegroundColor Yellow
    }
}

Write-Host "`nSync complete! Open Lemuroid on your TV to play." -ForegroundColor Green
