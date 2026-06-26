@echo off
setlocal
cd /d "%~dp0"
echo MiniGolf PWA mit Google-Drive-Test wird gestartet...
echo.
echo URL: http://localhost:8092
echo Beenden: Strg+C
node dev-server.js --port=8092
pause
