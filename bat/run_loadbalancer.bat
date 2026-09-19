@echo off
title Distributed POS - Load Balancer

cd /d "%~dp0.."

echo ==========================================
echo       DISTRIBUTED POS - LOAD BALANCER
echo ==========================================
echo.
echo Starting Load Balancer...
echo.

java -cp "out;lib\sqlite-jdbc-3.53.4.0.jar" com.lukaswhite.pos.loadbalancer.LoadBalancer

echo.
echo Load Balancer stopped.
pause