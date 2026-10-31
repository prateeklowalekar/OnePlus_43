<#
.SYNOPSIS
    Starts the CleanYourTV Web Application & API Server.
#>
$PSScriptRootLocal = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent $PSScriptRootLocal
$Py = Join-Path $ProjectRoot "tools\venv\Scripts\python.exe"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Starting CleanYourTV Web Dashboard...  " -ForegroundColor Green
Write-Host " URL: http://127.0.0.1:8000             " -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Cyan

# Open default browser
Start-Process "http://127.0.0.1:8000"

# Start FastAPI backend
Set-Location (Join-Path $ProjectRoot "server")
& $Py -m uvicorn main:app --host 127.0.0.1 --port 8000 --reload
