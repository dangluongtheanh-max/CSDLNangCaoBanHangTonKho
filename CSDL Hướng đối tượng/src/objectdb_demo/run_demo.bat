@echo off
chcp 65001 >nul
pushd "%~dp0"
echo =========================================================================
echo ĐỒ ÁN CSDL NÂNG CAO - CHẠY THỰC NGHIỆM OBJECTDB VỚI JPQL / OQL
echo =========================================================================
echo.
call mvn clean compile exec:java
set "MVN_EXIT=%errorlevel%"
echo.
echo =========================================================================
if not "%MVN_EXIT%"=="0" (
    echo [THAT BAI] Chay demo gap loi ^(exit code=%MVN_EXIT%^). Kiem tra log phia tren!
) else (
    echo ĐÃ THỰC THI XONG! KẾT QUẢ ĐÃ ĐƯỢC GHI VÀO target/execution_output.txt
)
echo =========================================================================
popd
setlocal enabledelayedexpansion
set "CMDLINE=!cmdcmdline!"
if not "!CMDLINE!"=="!CMDLINE:/c ""=!" pause
exit /b %MVN_EXIT%

