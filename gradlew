#!/bin/sh

# Gradle wrapper start script (generated for WSW Olomouc).
# Standard Gradle wrapper launcher.

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")/.." && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
