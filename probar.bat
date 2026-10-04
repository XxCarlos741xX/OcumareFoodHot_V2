@echo off
REM Compila y corre las pruebas. El resultado queda en build-log.txt
cd /d "%~dp0"
if exist "%USERPROFILE%\.jdks\corretto-21.0.10" set "JAVA_HOME=%USERPROFILE%\.jdks\corretto-21.0.10"
echo Ejecutando pruebas con JAVA_HOME=%JAVA_HOME% ... > build-log.txt
call gradlew.bat clean test --console=plain >> build-log.txt 2>&1
echo CODIGO_SALIDA=%ERRORLEVEL% >> build-log.txt
