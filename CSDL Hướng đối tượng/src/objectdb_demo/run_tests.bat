@echo off
chcp 65001 >nul
pushd "%~dp0"
echo =========================================================================
echo DO AN CSDL NANG CAO - CHAY BO KIEM THU JUNIT 5 PHAN HE CSDL HUONG DOI TUONG
echo (ObjectDB / JPA 2.2: OID, 6 OQL/JPQL, Ke thua/Da hinh, OOP Methods, ACID)
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

