## Installation

[Download](#download) | [Build from source](#build-from-source) | [Clojure CLI tools](#clojure-cli-tools)

### Download

Clerk uses GraalVM's Native Image utility to compile to a binary executable.

1. Download the [latest binary](https://github.com/jeff-bruemmer/clerk/releases) for your system. Linux and Mac OS (Darwin) binaries are available. Windows users can run Clerk on Windows Subsystem for Linux.
2. `cd` into your Downloads directory and rename download to `clerk`.
3. `chmod +x clerk`.
4. Add Clerk to your \$PATH, e.g., `sudo cp clerk /usr/local/bin`.

### Build from source

Build and install clerk from source:

```bash
# Clone the repository
git clone https://github.com/jeff-bruemmer/clerk.git
cd clerk

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
