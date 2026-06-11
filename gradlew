#!/bin/sh
#
# Gradle wrapper script for POSIX systems (Linux/macOS).
# Auto-downloads Gradle if not present.
#

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Prefer JAVA_HOME if set, otherwise fall back to PATH java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
