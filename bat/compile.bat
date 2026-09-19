@echo off
title Distributed POS - Compile

cd /d "%~dp0.."

echo ==========================================
echo       DISTRIBUTED POS - COMPILE
echo ==========================================
echo.

if not exist out mkdir out

javac -cp "lib\sqlite-jdbc-3.53.4.0.jar" -d out ^
 src\main\java\com\lukaswhite\pos\common\*.java ^
 src\main\java\com\lukaswhite\pos\server\*.java ^
 src\main\java\com\lukaswhite\pos\loadbalancer\*.java

if errorlevel 1 (
    echo.
    echo COMPILATION FAILED
    pause
    exit /b 1
)

echo.
echo Copying resources...
xcopy /E /I /Y "src\main\resources" "out"

echo.
echo ==========================================
echo COMPILATION COMPLETE
echo ==========================================
pause