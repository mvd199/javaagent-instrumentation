#!/bin/sh
# Checks that the agent JAR built by `mvn package` relocates javassist and still works when the
# application has its own javassist on the classpath. Needs `mvn test-compile package` first.
set -eu
JAR="${1:-target/java-instrumentation-1.0-SNAPSHOT.jar}"
M2_REPO="${M2_REPO:-$HOME/.m2/repository}"
HOST_JAVASSIST="$(find "$M2_REPO/org/javassist/javassist" -name 'javassist-*.jar' | head -n 1)"
fail() { echo "FAIL: $*" >&2; exit 1; }

[ -f "$JAR" ] || fail "no agent JAR at $JAR"
[ -n "$HOST_JAVASSIST" ] || fail "no javassist JAR under $M2_REPO"

# 1. The JAR holds javassist only under the relocated package.
ENTRIES="$(jar tf "$JAR")"
if echo "$ENTRIES" | grep -q '^javassist/'; then
    fail "$JAR contains unrelocated javassist classes"
fi
echo "$ENTRIES" | grep -q '^mvd/instrumentation/shaded/javassist/ClassPool.class$' \
    || fail "$JAR has no relocated javassist"

# 2. With the application's own javassist on the classpath, the agent uses its relocated copy and
#    still instruments and reports.
LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT
java -verbose:class -javaagent:"$JAR"=demo.cfg -cp "target/test-classes:$HOST_JAVASSIST" \
    mvd.demo.DemoApp > "$LOG" 2>&1 || { cat "$LOG" >&2; fail "demo exited with an error"; }
grep -q 'mvd\.instrumentation\.shaded\.javassist\.ClassPool source: .*'"$(basename "$JAR")" "$LOG" \
    || fail "agent did not load its relocated javassist"
if grep -q '\] javassist\.' "$LOG"; then
    fail "agent loaded the application's javassist"
fi
grep -q 'recorded: [1-9]' "$LOG" || { cat "$LOG" >&2; fail "demo report recorded no calls"; }

echo "Shading OK: javassist relocated to mvd.instrumentation.shaded.javassist"
