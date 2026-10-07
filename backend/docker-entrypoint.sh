#!/bin/sh
set -e

# Named Docker volumes mounted over /app/uploads and /app/logs are owned by
# root:root and mask the chown done at image build time. The Spring Boot process
# runs as the non-root "appuser", so without this fixup it cannot create
# subdirectories/files under the mounted volumes -> java.nio AccessDeniedException,
# surfaced to the client as "Failed to upload file:/app/uploads/...".
#
# This script runs as root, repairs ownership of the writable volumes, then drops
# privileges back to appuser before launching the JVM.

for dir in /app/uploads /app/logs; do
    if [ -d "$dir" ]; then
        chown -R appuser:appuser "$dir" 2>/dev/null || \
            echo "WARN: could not chown $dir (continuing)"
    fi
done

exec gosu appuser java -jar -Xms512m -Xmx1024m app.jar
