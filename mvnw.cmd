@REM ----------------------------------------------------------------------------
@REM Lightweight Maven Wrapper for Windows (only-script). Uses PowerShell to download
@REM the Maven version from .mvn\wrapper\maven-wrapper.properties into %USERPROFILE%\.m2\wrapper\dists
@REM To replace it with the official Apache wrapper:  mvn -N wrapper:wrapper -Dtype=only-script
@REM ----------------------------------------------------------------------------
@echo off
setlocal
set "MVNW_BASEDIR=%~dp0"
set "MVNW_HOME_FILE=%TEMP%\mvnw-home-%RANDOM%.txt"

powershell -NoProfile -ExecutionPolicy Bypass -File "%MVNW_BASEDIR%.mvn\wrapper\mvnw-bootstrap.ps1" -BaseDir "%MVNW_BASEDIR%" > "%MVNW_HOME_FILE%"
if errorlevel 1 (
  echo Failed to bootstrap Maven 1>&2
  exit /b 1
)
set /p MVNW_MAVEN_HOME=<"%MVNW_HOME_FILE%"
del "%MVNW_HOME_FILE%" >nul 2>&1

set "MAVEN_PROJECTBASEDIR=%MVNW_BASEDIR:~0,-1%"
"%MVNW_MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
