@echo off
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0scripts\Start-Folio.ps1"
if errorlevel 1 pause
