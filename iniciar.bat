@echo off
REM Arranca Ocumare Food en modo de prueba (base en memoria con datos de ejemplo).
REM Abre http://localhost:8080 en el navegador. Para apagarlo, cierra esta ventana.
cd /d "%~dp0"
if exist "%USERPROFILE%\.jdks\corretto-21.0.10" set "JAVA_HOME=%USERPROFILE%\.jdks\corretto-21.0.10"
title Ocumare Food - servidor de prueba
call gradlew.bat bootRun --console=plain
