@echo off
title Distributed POS - Server Node 2

cd /d "%~dp0.."

echo ==========================================
echo       DISTRIBUTED POS - SERVER NODE 2
echo ==========================================
echo.
echo Starting server on:
echo 127.0.0.1:8002
echo.

java -cp "out;lib\sqlite-jdbc-3.53.4.0.jar" com.lukaswhite.pos.server.ServerNode 127.0.0.1 8002

echo.
echo Server Node 2 stopped.
pause