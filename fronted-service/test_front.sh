
#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "====================================="
echo " Colizant - Frontend Regression Tests"
echo "====================================="

if command -v mvn >/dev/null 2>&1; then
    mvn -B test
elif [[ -x ./mvnw ]]; then
    ./mvnw -B test
else
    echo "ERROR: Maven is not installed."
    exit 127
fi
