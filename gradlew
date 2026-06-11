#!/bin/sh
# Gradle start up script for UN*X
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export JAVA_HOME
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
exec "$JAVA_HOME/bin/java" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
