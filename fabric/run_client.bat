@echo off
cd /d "%~dp0"
echo ========================================================
echo  Launching Gouge (Minecraft 26.2 - Fabric Client)
echo ========================================================
call .\gradlew.bat runClient
pause
