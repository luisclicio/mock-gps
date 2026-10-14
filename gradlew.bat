@echo off
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_SHA256=81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f

if not exist "%WRAPPER_JAR%" (
    where curl.exe >nul 2>nul || (
        echo Gradle Wrapper bootstrap requires curl.exe on the first run.
        exit /b 1
    )
    curl.exe -fsSL "https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar" -o "%WRAPPER_JAR%.tmp"
    if errorlevel 1 exit /b 1
    powershell.exe -NoProfile -Command "$hash=(Get-FileHash -Algorithm SHA256 -LiteralPath '%WRAPPER_JAR%.tmp').Hash.ToLower(); if ($hash -ne '%WRAPPER_SHA256%') { exit 1 }"
    if errorlevel 1 (
        del /q "%WRAPPER_JAR%.tmp" >nul 2>nul
        echo Downloaded Gradle Wrapper JAR failed SHA-256 verification.
        exit /b 1
    )
    move /y "%WRAPPER_JAR%.tmp" "%WRAPPER_JAR%" >nul
    if errorlevel 1 exit /b 1
)

powershell.exe -NoProfile -Command "$hash=(Get-FileHash -Algorithm SHA256 -LiteralPath '%WRAPPER_JAR%').Hash.ToLower(); if ($hash -ne '%WRAPPER_SHA256%') { exit 1 }"
if errorlevel 1 (
    echo Gradle Wrapper JAR failed SHA-256 verification.
    exit /b 1
)

if defined JAVA_HOME (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
    set JAVACMD=java.exe
)

"%JAVACMD%" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
