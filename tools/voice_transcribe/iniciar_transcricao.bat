@echo off
setlocal
cd /d "%~dp0"
set "SCRIPT=%~dp0transcribe_ptt.py"

where py >nul 2>&1
if %errorlevel%==0 (
  py -3 -m pip install -q -r requirements.txt
  if errorlevel 1 goto :err
  where pyw >nul 2>&1
  if %errorlevel%==0 (
    start "" pyw -3 "%SCRIPT%"
  ) else (
    start "" py -3 "%SCRIPT%"
  )
  exit /b 0
)

where python >nul 2>&1
if %errorlevel%==0 (
  python -m pip install -q -r requirements.txt
  if errorlevel 1 goto :err
  where pythonw >nul 2>&1
  if %errorlevel%==0 (
    start "" pythonw "%SCRIPT%"
  ) else (
    start "" python "%SCRIPT%"
  )
  exit /b 0
)

echo ERRO: Python nao encontrado.
pause
exit /b 1

:err
pause
exit /b 1
