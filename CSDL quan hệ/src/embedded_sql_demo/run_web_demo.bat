@echo off
chcp 65001 >nul
pushd "%~dp0"
echo =========================================================================
echo DO AN CSDL NANG CAO - KHOI DONG MINI WEB DEMO (POSTGRESQL 17 + OBJECTDB)
echo Dia chi: http://127.0.0.1:8080
echo Dong tab trinh duyet (hoac nhan Ctrl + C / nut Stop trong IDE) de tu dong tat server.
echo =========================================================================
echo.
call mvn compile exec:java -Dexec.mainClass=com.csdl.rdb.WebDemoServer
set "MVN_EXIT=%errorlevel%"
echo.
popd
setlocal enabledelayedexpansion
set "CMDLINE=!cmdcmdline!"
if not "!CMDLINE!"=="!CMDLINE:/c ""=!" pause
exit /b %MVN_EXIT%
