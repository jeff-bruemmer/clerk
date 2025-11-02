# Installation

## Quick Install

```bash
git clone https://github.com/jeff-bruemmer/proserunner.git
cd proserunner
./install.sh
```

This installs the adaptive wrapper that auto-selects between Clojure and Babashka based on workload size.

Requires: Java and Clojure CLI (installer will guide you if missing)

## Optional: Install Babashka

For faster startup on small workloads (~9ms vs ~600ms):

```bash
# macOS
brew install babashka

# Linux
curl -s https://raw.githubusercontent.com/babashka/babashka/master/install | bash
```

The wrapper automatically uses bb when available for ≤50 files.

## Optional: Build Native Binary

For fastest startup (~50ms):

```bash
bb build && bb install
```

Requires: GraalVM with native-image. See [building.md](building.md).

## Pre-built Binaries

Download from [releases](https://github.com/jeff-bruemmer/proserunner/releases), make executable, move to PATH.
