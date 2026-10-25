@echo off
REM Puente de impresion de Ocumare Food para una impresora termica de red (ESC/POS).
REM 1. Pega en SERVIDOR la direccion del puente (Gestion, Impresoras, Agregar impresora).
REM 2. Pon en IMPRESORA la IP de la impresora (sale en la hoja de autoprueba de la impresora).
REM 3. Guarda y dale doble clic. Deja esta ventana abierta mientras el local este trabajando.
set "SERVIDOR=https://ocumarefoodhot.com/api/impresion/puente/PEGA-AQUI-EL-TOKEN"
set "IMPRESORA=192.168.1.100"
set "PUERTO=9100"

cd /d "%~dp0"
title Ocumare Food - puente de impresora de cocina
if exist "%USERPROFILE%\.jdks\corretto-21.0.10\bin\java.exe" (set "JAVA=%USERPROFILE%\.jdks\corretto-21.0.10\bin\java.exe") else (set "JAVA=java")
:repetir
"%JAVA%" PuenteImpresora.java
echo El puente se detuvo. Se vuelve a abrir en 10 segundos...
timeout /t 10 /nobreak >nul
goto repetir
