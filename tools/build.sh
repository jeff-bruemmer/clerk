#!/bin/bash
## Exit if any command fails.
set -e

## Set the table.

echo "Initializing build..."

## Remove existing classes directory and clerk binary.

echo "Removing classes directory."

rm -rf classes

echo "Removing .cpcache directory."

rm -rf .cpcache

echo "Building native image."

echo "This could take a bit..."

echo "If you run into issues, check out:"

echo "https://github.com/clj-easy/graal-docs"

clojure -M:build

echo "Native image built successfully."

echo "Making the clerk image executable."

chmod +x clerk

## Bus the table

echo "Cleaning up..."

rm -rf classes

rm -rf .cpcache

echo "Done."

echo "Running clerk -h"

./clerk -h


