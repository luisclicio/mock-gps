#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || exit 1
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_SHA256=81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f

if [ ! -f "$WRAPPER_JAR" ]; then
    command -v curl >/dev/null 2>&1 || {
        echo "Gradle Wrapper bootstrap requires curl on the first run." >&2
        exit 1
    }
    TEMP_WRAPPER="$WRAPPER_JAR.tmp.$$"
    trap 'rm -f "$TEMP_WRAPPER"' EXIT HUP INT TERM
    curl -fsSL "https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar" -o "$TEMP_WRAPPER" || exit 1

    if command -v sha256sum >/dev/null 2>&1; then
        ACTUAL_SHA256=$(sha256sum "$TEMP_WRAPPER")
        ACTUAL_SHA256=${ACTUAL_SHA256%% *}
    elif command -v shasum >/dev/null 2>&1; then
        ACTUAL_SHA256=$(shasum -a 256 "$TEMP_WRAPPER")
        ACTUAL_SHA256=${ACTUAL_SHA256%% *}
    else
        echo "Gradle Wrapper bootstrap requires sha256sum or shasum." >&2
        exit 1
    fi

    if [ "$ACTUAL_SHA256" != "$WRAPPER_SHA256" ]; then
        echo "Downloaded Gradle Wrapper JAR failed SHA-256 verification." >&2
        exit 1
    fi
    mv "$TEMP_WRAPPER" "$WRAPPER_JAR"
    trap - EXIT HUP INT TERM
fi

if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256=$(sha256sum "$WRAPPER_JAR")
    ACTUAL_SHA256=${ACTUAL_SHA256%% *}
elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA256=$(shasum -a 256 "$WRAPPER_JAR")
    ACTUAL_SHA256=${ACTUAL_SHA256%% *}
else
    echo "Gradle Wrapper bootstrap requires sha256sum or shasum." >&2
    exit 1
fi

if [ "$ACTUAL_SHA256" != "$WRAPPER_SHA256" ]; then
    echo "Gradle Wrapper JAR failed SHA-256 verification." >&2
    exit 1
fi

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

exec "$JAVACMD" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
