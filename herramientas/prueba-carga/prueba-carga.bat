@echo off
REM Prueba de carga: reinicia el servidor de prueba con los limites por IP altos
REM (todos los clientes simulados salen de este mismo PC) y corre carga.mjs con Node.
REM El informe queda en carga.log. Al terminar, el servidor sigue corriendo en su ventana.
cd /d "%~dp0..\.."
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /r /c:":8080 .*LISTENING" /c:":8080 .*ESCUCHANDO"') do taskkill /PID %%p /F >nul 2>&1
timeout /t 3 /nobreak >nul
if exist "%USERPROFILE%\.jdks\corretto-21.0.10" set "JAVA_HOME=%USERPROFILE%\.jdks\corretto-21.0.10"
start "Ocumare Food - servidor (prueba de carga)" cmd /k gradlew.bat bootRun --console=plain "--args=--ocumare.limites.pedidos-web=100000 --ocumare.limites.consultas-web=100000 --ocumare.limites.api=100000 --ocumare.limites.login=1000"
echo Corriendo la prueba de carga... > "%~dp0carga.log"
node -v >> "%~dp0carga.log" 2>&1
node "%~dp0carga.mjs" >> "%~dp0carga.log" 2>&1
echo CODIGO_SALIDA=%ERRORLEVEL%>> "%~dp0carga.log"
