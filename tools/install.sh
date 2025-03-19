#!/bin/bash
## Exit if any command fails.
set -e

echo "Building clerk binary..."
./tools/build.sh

echo "Moving clerk binary to /usr/local/bin directory."
echo "You may need to sudo this command."

sudo mv clerk /usr/local/bin

echo "Installation complete!"

