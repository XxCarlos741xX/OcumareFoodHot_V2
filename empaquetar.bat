@echo off
REM Arma el paquete para el servidor: build\libs\ocumare.jar
cd /d "%~dp0"
if exist "%USERPROFILE%\.jdks\corretto-21.0.10" set "JAVA_HOME=%USERPROFILE%\.jdks\corretto-21.0.10"
echo Armando el paquete... > build-log.txt
call gradlew.bat clean test bootJar --console=plain >> build-log.txt 2>&1
echo CODIGO_SALIDA=%ERRORLEVEL% >> build-log.txt
if exist build\libs\ocumare.jar (echo. & echo Paquete listo: %~dp0build\libs\ocumare.jar) else (echo. & echo Algo fallo. Revisa build-log.txt)
pause
