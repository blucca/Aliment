@echo off
rem Regenerates every willow texture with ImageMagick. Run from anywhere.
where pwsh >nul 2>nul
if %ERRORLEVEL%==0 (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0gen_textures.ps1" %*
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0gen_textures.ps1" %*
)
