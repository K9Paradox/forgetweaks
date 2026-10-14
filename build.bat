@echo off
rem ===========================================================================
rem  RLUtility build script (Windows)
rem
rem  ForgeGradle 2.3 only runs on JDK 8 and Gradle 4.x, which is almost never
rem  what a modern machine has on PATH. This script finds a JDK 8, downloads a
rem  private copy of Gradle 4.10.3 if needed, locates your RLCraft mods folder
rem  and then builds. Nothing is installed system wide.
rem
rem  The full build output is saved to build-last.log next to this file, so if
rem  something fails you can open/paste that instead of scrolling.
rem
rem  Usage:
rem    build.bat
rem    build.bat "C:\path\to\RLCraft\minecraft\mods"
rem ===========================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "GRADLE_VER=4.10.3"
set "CACHE=%USERPROFILE%\.rlutility-build"
set "GRADLE_HOME=%CACHE%\gradle-%GRADLE_VER%"
set "BUILDLOG=%CD%\build-last.log"

echo.
echo === RLUtility build ===
echo.

rem --------------------------------------------------------------- find JDK 8
set "JDK8="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /C:"1.8." >nul && set "JDK8=%JAVA_HOME%"
    )
)

if not defined JDK8 (
    for %%R in (
        "%ProgramFiles%\Java"
        "%ProgramFiles%\Eclipse Adoptium"
        "%ProgramFiles%\Eclipse Foundation"
        "%ProgramFiles%\AdoptOpenJDK"
        "%ProgramFiles%\Amazon Corretto"
        "%ProgramFiles%\Zulu"
        "%ProgramFiles%\Microsoft"
        "%ProgramFiles%\BellSoft"
        "%ProgramFiles(x86)%\Java"
        "%USERPROFILE%\.jdks"
        "%APPDATA%\PrismLauncher\java"
        "%APPDATA%\.minecraft\runtime"
        "%USERPROFILE%\curseforge\minecraft\Install\runtime"
    ) do (
        if exist %%R (
            for /d %%D in (%%~R\*) do (
                if not defined JDK8 (
                    if exist "%%D\bin\java.exe" (
                        "%%D\bin\java.exe" -version 2>&1 | findstr /C:"1.8." >nul && set "JDK8=%%D"
                    )
                    rem one extra level down, e.g. runtime\java-runtime-alpha\windows-x64\...
                    if not defined JDK8 (
                        for /d %%E in (%%D\*) do (
                            if not defined JDK8 if exist "%%E\bin\java.exe" (
                                "%%E\bin\java.exe" -version 2>&1 | findstr /C:"1.8." >nul && set "JDK8=%%E"
                            )
                        )
                    )
                )
            )
        )
    )
)

if not defined JDK8 (
    echo [ERROR] No JDK 8 found. ForgeGradle 2.3 cannot run on Java 9 or newer.
    echo.
    echo Install one, then re-run this script:
    echo   winget install EclipseAdoptium.Temurin.8.JDK
    echo or download from https://adoptium.net/temurin/releases/?version=8
    echo.
    pause
    exit /b 1
)

echo [1/4] JDK 8:  %JDK8%
set "JAVA_HOME=%JDK8%"

rem -------------------------------------------------------------- fetch Gradle
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
    echo [2/4] Downloading Gradle %GRADLE_VER% ^(one time, ~85 MB^)...
    if not exist "%CACHE%" mkdir "%CACHE%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
        "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip' -OutFile '%CACHE%\gradle.zip';" ^
        "Expand-Archive -Path '%CACHE%\gradle.zip' -DestinationPath '%CACHE%' -Force;" ^
        "Remove-Item '%CACHE%\gradle.zip'"
    if not exist "%GRADLE_HOME%\bin\gradle.bat" (
        echo [ERROR] Gradle download failed. Check your internet connection.
        echo.
        pause
        exit /b 1
    )
) else (
    echo [2/4] Gradle %GRADLE_VER%: cached
)

rem ------------------------------------------------------------ find mods dir
set "MODS=%~1"
if not defined MODS set "MODS=%RLCRAFT_MODS%"

if not defined MODS (
    for %%M in (
        "%APPDATA%\PrismLauncher\instances\RLCraft\minecraft\mods"
        "%APPDATA%\PrismLauncher\instances\RLCraft\.minecraft\mods"
        "%APPDATA%\.multimc\instances\RLCraft\.minecraft\mods"
        "%USERPROFILE%\curseforge\minecraft\Instances\RLCraft\mods"
        "%APPDATA%\.minecraft\mods"
    ) do (
        if not defined MODS if exist "%%~M\Level Up- 2-1.1.23-1.12.jar" set "MODS=%%~M"
    )
)

if not defined MODS (
    for /d %%I in ("%APPDATA%\PrismLauncher\instances\*") do (
        if not defined MODS if exist "%%I\minecraft\mods\Level Up- 2-1.1.23-1.12.jar" set "MODS=%%I\minecraft\mods"
    )
)

if not defined MODS set "MODS=%CD%\libs"

echo [3/4] Mods dir: %MODS%
if not exist "%MODS%\Level Up- 2-1.1.23-1.12.jar" (
    echo.
    echo [WARN] The RLCraft mod jars were not found there. Compilation will fail,
    echo        because this mod links against Level Up! 2, Reskillable, Locks etc.
    echo        Re-run as:  build.bat "C:\path\to\RLCraft\minecraft\mods"
    echo.
)

rem -------------------------------------------------------------------- build
echo [4/4] Building ^(first run decompiles Minecraft, expect 5-15 minutes^)...
echo       Output is echoed here and saved to build-last.log
echo.

rem Gradle output goes to the log file, then the log is printed to the screen.
rem That keeps a copy of everything even when the window would otherwise scroll away.
call "%GRADLE_HOME%\bin\gradle.bat" build -x test --no-daemon -PrlcraftMods="%MODS%" > "%BUILDLOG%" 2>&1
set "GRADLE_RC=%ERRORLEVEL%"
type "%BUILDLOG%"
echo.

if not "%GRADLE_RC%"=="0" (
    echo =========================================================================
    echo  [ERROR] Build failed ^(exit code %GRADLE_RC%^).
    echo  The complete output above is saved in:
    echo      %BUILDLOG%
    echo  Paste the section containing "error:" into the chat.
    echo =========================================================================
    echo.
    pause
    exit /b 1
)

echo =========================================================================
echo  Done. Jar(s) built:
for %%F in ("%CD%\build\libs\rlutility-*.jar") do echo      %%~fF
echo  Copy the jar into your RLCraft mods folder.
echo =========================================================================
echo.
pause
endlocal
