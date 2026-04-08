@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar_arranque_automatico.ps1"
if errorlevel 1 pause
exit /b %errorlevel%
