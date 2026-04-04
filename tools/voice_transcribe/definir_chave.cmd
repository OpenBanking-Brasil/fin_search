@echo off
setlocal
cd /d "%~dp0"
title Definir chave OpenAI
echo A abrir PowerShell nesta pasta...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0definir_chave.ps1"
if errorlevel 1 (
  echo.
  echo Ocorreu um erro. Codigo: %ERRORLEVEL%
  pause
)
