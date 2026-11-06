@echo off
REM Open local account maintenance with masked password entry, then keep the result visible.
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\create-account.ps1"
pause
