#!/bin/bash
## Exit if any command fails.
set -e

## Remove existing classes directory and clerk binary
rm -rf classes/ clerk

clojure -M:build

chmod +x clerk

sudo cp clerk /usr/local/bin
