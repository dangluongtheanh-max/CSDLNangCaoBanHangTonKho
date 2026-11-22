@echo off
chcp 65001 >nul
pushd "%~dp0"
echo =========================================================================
echo DO AN CSDL NANG CAO - CHAY DEMO SQL NHUNG (JDBC) & CANH TRANH 2 LUONG
echo Mac dinh ket noi Docker PostgreSQL: localhost:5433 / db: ql_banhang
echo =========================================================================
echo.
call mvn clean compile exec:java
set "MVN_EXIT=%errorlevel%"
echo.
echo =========================================================================
if not "%MVN_EXIT%"=="0" (
    echo [THAT BAI] Chay demo gap loi ^(exit code=%MVN_EXIT%^). Kiem tra log phia tren!
) else (
    echo DA CHAY XONG! LOG DUOC LUU TAI target/embedded_sql_output.txt
)
echo =========================================================================
popd
setlocal enabledelayedexpansion
set "CMDLINE=!cmdcmdline!"
if not "!CMDLINE!"=="!CMDLINE:/c ""=!" pause
exit /b %MVN_EXIT%

