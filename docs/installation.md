## Installation

[Download](#download) | [Build from source](#build-from-source) | [Clojure CLI tools](#clojure-cli-tools)

### Download

Proserunner uses GraalVM's Native Image utility to compile to a binary executable.

1. Download the [latest binary](https://github.com/jeff-bruemmer/proserunner/releases) for your system. Linux and Mac OS (Darwin) binaries are available. Windows users can run Proserunner on Windows Subsystem for Linux.
2. `cd` into your Downloads directory and rename download to `proserunner`.
3. `chmod +x proserunner`.
4. Add Proserunner to your \$PATH, e.g., `sudo cp proserunner /usr/local/bin`.

### Build from source

Build and install proserunner from source:

```bash
# Clone the repository
git clone https://github.com/jeff-bruemmer/proserunner.git
cd proserunner

# Build and install (interactive)
./tools/install.sh
```

The install script will:

1. Check for required dependencies (Clojure, GraalVM)
2. Build the native binary (~60 seconds)
3. Offer installation options (system-wide or user-only)

For detailed instructions, prerequisites, and troubleshooting, see [Building from Source](building.md).

### Clojure CLI tools

Run with Clojure's CLI tools.

```
clj -M:run -f /path/to/file
```
