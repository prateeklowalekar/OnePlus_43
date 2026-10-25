<#
.SYNOPSIS
    Syncs local static and live wallpapers from PC to connected Android TV over ADB.
.PARAMETER DeviceIp
    Target TV IP address (defaults to 192.168.1.29:5555 for TV2).
#>
param(
    [string]$DeviceIp = "192.168.1.29:5555"
)

$PSScriptRootLocal = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent $PSScriptRootLocal
$Adb = Join-Path $ProjectRoot "tools\platform-tools\adb.exe"

$StaticFolder = Join-Path $ProjectRoot "custom_wallpapers\static"
$LiveFolder = Join-Path $ProjectRoot "custom_wallpapers\live"

Write-Host "Connecting to TV at $DeviceIp..." -ForegroundColor Cyan
& $Adb connect $DeviceIp

Write-Host "Ensuring directories on TV..." -ForegroundColor Cyan
& $Adb -s $DeviceIp shell "mkdir -p /sdcard/Wallpapers /sdcard/Wallpapers/Live /sdcard/Android/data/com.gathin.tvhome/files/Wallpapers /sdcard/Android/data/com.gathin.tvhome/files/LiveWallpapers"

# Push static wallpapers
$staticFiles = Get-ChildItem -Path $StaticFolder -File | Where-Object { $_.Extension -match '^\.(jpg|jpeg|png|webp)$' }
if ($staticFiles.Count -gt 0) {
    Write-Host "Pushing $($staticFiles.Count) static wallpaper(s)..." -ForegroundColor Green
    foreach ($file in $staticFiles) {
        Write-Host " -> $($file.Name)"
        & $Adb -s $DeviceIp push $file.FullName "/sdcard/Wallpapers/$($file.Name)"
        & $Adb -s $DeviceIp push $file.FullName "/sdcard/Android/data/com.gathin.tvhome/files/Wallpapers/$($file.Name)"
    }
} else {
    Write-Host "No static wallpapers found in custom_wallpapers\static" -ForegroundColor Yellow
}

# Push live wallpapers
$liveFiles = Get-ChildItem -Path $LiveFolder -File | Where-Object { $_.Extension -match '^\.(mp4|mov|mkv)$' }
if ($liveFiles.Count -gt 0) {
    Write-Host "Pushing $($liveFiles.Count) live wallpaper(s)..." -ForegroundColor Green
    foreach ($file in $liveFiles) {
        Write-Host " -> $($file.Name)"
        & $Adb -s $DeviceIp push $file.FullName "/sdcard/Wallpapers/Live/$($file.Name)"
        & $Adb -s $DeviceIp push $file.FullName "/sdcard/Android/data/com.gathin.tvhome/files/LiveWallpapers/$($file.Name)"
    }
} else {
    Write-Host "No live wallpapers found in custom_wallpapers\live" -ForegroundColor Yellow
}

Write-Host "`nSync complete! Open Wallpaper Picker on TV to apply." -ForegroundColor Cyan
