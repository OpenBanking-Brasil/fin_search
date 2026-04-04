@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Chave OpenAI — cola NO Notepad, NAO no PowerShell
cls
echo.
echo  ============================================================
echo    NAO colas a chave na janela azul do PowerShell como comando.
echo    Vai abrir o Bloco de notas: cola UMA linha ^(comeca por sk-^).
echo    Depois: Ficheiro - Guardar ^(Ctrl+S^) e fecha o Notepad.
echo  ============================================================
echo.
if not exist ".openai_key" (echo.>".openai_key")
pause
start "" notepad "%~dp0.openai_key"
echo.
echo Quando guardares, podes iniciar: iniciar_transcricao.bat
pause
