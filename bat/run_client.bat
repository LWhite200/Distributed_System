@echo off
title Distributed POS - Client

cd /d "%~dp0.."

echo ==========================================
echo       DISTRIBUTED POS - CLIENT
echo ==========================================
echo.
echo Starting JavaFX Client...
echo.

java --module-path "lib\javaFX\lib" ^
 --add-modules javafx.controls,javafx.fxml ^
 -cp "out;lib\sqlite-jdbc-3.53.4.0.jar" ^
 com.lukaswhite.pos.client.Client

echo.
echo Client stopped.
pause