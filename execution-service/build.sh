#!/bin/bash
set -e

echo "Building job-execution-service Docker image..."

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Build Docker image
docker build -t patmeshs/job-execution-service:latest .

echo " job-service image built successfully!"
echo "  Image: patmeshs/job-execution-service:latest"