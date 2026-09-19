@echo off
title Distributed POS - Compile Client

cd /d "%~dp0.."

echo ==========================================
echo       DISTRIBUTED POS - COMPILE CLIENT
echo ==========================================
echo.

if not exist out mkdir out

echo Compiling JavaFX client...
echo.

javac --module-path "lib\javaFX\lib" ^
 --add-modules javafx.controls,javafx.fxml ^
 -cp "out;lib\sqlite-jdbc-3.53.4.0.jar" ^
 -d out ^
 src\main\java\com\lukaswhite\pos\client\Client.java

if errorlevel 1 (
    echo.
    echo ==========================================
    echo CLIENT COMPILATION FAILED
    echo ==========================================
    echo.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo CLIENT COMPILATION COMPLETE
echo ==========================================
echo.

pause