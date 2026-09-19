@echo off
title Distributed POS - Server Node 1

cd /d "%~dp0.."

echo ==========================================
echo       DISTRIBUTED POS - SERVER NODE 1
echo ==========================================
echo.
echo Starting server on:
echo 127.0.0.1:8001
echo.

java -cp "out;lib\sqlite-jdbc-3.53.4.0.jar" com.lukaswhite.pos.server.ServerNode 127.0.0.1 8001

echo.
echo Server Node 1 stopped.
pause