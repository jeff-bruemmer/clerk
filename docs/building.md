# Building Proserunner from Source

This guide covers building Proserunner's native binary from source using GraalVM.

## Prerequisites

### Required

- **Clojure CLI tools**: Install from https://clojure.org/guides/install_clojure
- **GraalVM with native-image**: See installation options below

### GraalVM Installation

#### Option 1: Download from Oracle (Recommended)

```bash
# Download GraalVM 21 for your platform
cd ~
mkdir graalvm
cd graalvm
curl -L -o graalvm.tar.gz https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz
tar -xzf graalvm.tar.gz

# Set environment variables (add to ~/.bashrc for persistence)
export JAVA_HOME=~/graalvm/graalvm-jdk-21.0.9+7.1
export PATH=$JAVA_HOME/bin:$PATH

# Verify installation
java -version    # Should show GraalVM
native-image --version
```

#### Option 2: SDKMAN (Linux/macOS)

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21-graal
```

## Building

The build script automatically checks prerequisites and provides helpful error messages:

```bash
./tools/build.sh
```

Build time: 30-60 seconds. Binary size: ~60MB.

## Installing

After building, install proserunner to your system:

```bash
./tools/install.sh
```

### Installation Options

The install script offers three choices:

**1) System-wide installation** (`/usr/local/bin`)

- Requires sudo
- Available to all users
- Automatically in PATH

**2) User-only installation** (`~/.local/bin`)

- No sudo required
- Only available to your user
- May need to add to PATH:
  ```bash
  echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
  source ~/.bashrc
  ```

**3) Cancel**

- Binary remains in project directory
- Can run as `./proserunner` from project root

## Running without Building

If you don't want to build a native binary, you can run Proserunner with the Clojure CLI:

```bash
clojure -M:run -f /path/to/file.md
```

This is slower on startup but doesn't require GraalVM.
