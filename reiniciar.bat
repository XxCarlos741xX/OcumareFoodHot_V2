@echo off
REM Detiene el servidor de prueba que esté usando el puerto 8080 y lo vuelve a arrancar.
cd /d "%~dp0"
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /r /c:":8080 .*LISTENING"') do (
  echo Deteniendo el servidor anterior (proceso %%p)...
  taskkill /PID %%p /F >nul 2>&1
)
timeout /t 2 /nobreak >nul
call "%~dp0iniciar.bat"
