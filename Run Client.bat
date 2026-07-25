@echo off
setlocal
title Character Select - dev client

rem Always work from the folder this script lives in, so it can be clicked from anywhere.
pushd "%~dp0"

rem %~dp0 falls back to the current directory when the script is invoked without a path, so
rem confirm we actually landed next to the wrapper before blaming Gradle for anything.
if not exist "gradlew.bat" (
    echo [charselect] Could not find gradlew.bat next to this script.
    echo [charselect] Expected it in: %~dp0
    echo [charselect] Keep Run Client.bat in the project root.
    echo.
    pause
    popd
    exit /b 1
)

rem NeoForge regenerates fml.toml without the key its own splash screen reads, which crashes
rem the client before Minecraft's window opens. Put it back if it went missing again.
rem Kept flat with goto rather than nested if-blocks: cmd mis-parses "(echo.& echo x)>>file"
rem when it sits inside another parenthesised block.
set "FMLCFG=runs\client\config\fml.toml"
if not exist "%FMLCFG%" goto launch
findstr /c:"earlyWindowDarkMode" "%FMLCFG%" >nul 2>&1
if not errorlevel 1 goto launch
echo [charselect] Restoring earlyWindowDarkMode in fml.toml
echo.>>"%FMLCFG%"
echo earlyWindowDarkMode = false>>"%FMLCFG%"

:launch
echo [charselect] Starting the dev client...
echo.
call "%~dp0gradlew.bat" runClient --console=plain
set CODE=%ERRORLEVEL%
if not "%CODE%"=="0" goto failed
goto done

:failed
echo.
echo [charselect] The client exited with code %CODE%.
echo [charselect] Scroll up for the error, or see runs\client\logs\latest.log
echo.
pause

:done
popd
endlocal
