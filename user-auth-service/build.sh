#!/bin/bash
set -e

echo "Building auth-service Docker image..."

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Build Docker image
docker build -t patmeshs/auth-service:latest .

echo " auth-service image built successfully!"
echo "  Image: patmeshs/auth-service:latest"
