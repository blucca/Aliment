@echo off
rem Regenerates the willow data / asset JSON files. Run from anywhere.
where pwsh >nul 2>nul
if %ERRORLEVEL%==0 (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0gen_data.ps1" %*
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0gen_data.ps1" %*
)
