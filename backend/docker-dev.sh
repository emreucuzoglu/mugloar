#!/bin/sh
# Runs the backend inside the dev container (see compose.yaml) and recompiles on
# changes. Spring Boot DevTools restarts the app when the compiled classes change.
# Polls because file events from the host don't reach the container reliably.

fingerprint() {
    find src build.gradle -type f -printf '%p %T@\n' | sort | md5sum
}

last=$(fingerprint)

./gradlew bootRun --console=plain &
app=$!

while sleep 2; do
    current=$(fingerprint)
    if [ "$current" != "$last" ]; then
        last=$current
        ./gradlew classes --console=plain
        # A failed first compile ends bootRun, so start it again once the code has changed.
        if ! kill -0 "$app" 2>/dev/null; then
            ./gradlew bootRun --console=plain &
            app=$!
        fi
    fi
done
