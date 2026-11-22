@echo off
chcp 65001 >nul
pushd "%~dp0"
echo =========================================================================
echo DO AN CSDL NANG CAO - CHAY BO KIEM THU JUNIT 5 (18 BANG, 12 QUERIES,
echo   7 FUNCTIONS/PROCEDURES, 8 NHOM TRIGGER, 18 INDEX, CONCURRENCY 2 LUONG)
echo Mac dinh ket noi Docker PostgreSQL: localhost:5433 / db: ql_banhang
echo =========================================================================
echo.
call mvn test
set "MVN_EXIT=%errorlevel%"
echo.
echo =========================================================================
if not "%MVN_EXIT%"=="0" (
    echo [THAT BAI] Kiem thu gap loi ^(exit code=%MVN_EXIT%^). Kiem tra log phia tren!
) else (
    echo HOAN TAT KIEM THU! BAO CAO CHI TIET TAI target/surefire-reports/
)
echo =========================================================================
popd
setlocal enabledelayedexpansion
set "CMDLINE=!cmdcmdline!"
if not "!CMDLINE!"=="!CMDLINE:/c ""=!" pause
exit /b %MVN_EXIT%

