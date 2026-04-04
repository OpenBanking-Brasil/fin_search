@echo off
REM Abre PowerShell ja nesta pasta (sem comandos com aspas que partem no Windows).
cd /d "%~dp0"
start "" powershell.exe -NoExit -NoProfile -WorkingDirectory "%~dp0"
