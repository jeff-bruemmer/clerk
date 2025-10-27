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

Build time: 30-60 seconds
Binary size: ~60MB

### What the build script does:

1. Checks for clojure and native-image
2. Cleans previous build artifacts
3. Compiles Clojure code with GraalVM native-image
4. Creates executable binary
5. Tests the binary

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

## Troubleshooting

### "native-image command not found"

- Ensure GraalVM is installed
- Check that `JAVA_HOME` points to GraalVM installation
- Verify `$JAVA_HOME/bin` is in your `PATH`
- Run `which native-image` to confirm it's accessible

### "clojure command not found"

- Install Clojure CLI tools: https://clojure.org/guides/install_clojure
- On Linux: `curl -O https://download.clojure.org/install/linux-install-1.11.1.1189.sh && chmod +x linux-install-1.11.1.1189.sh && sudo ./linux-install-1.11.1.1189.sh`

### Build fails with "Unable to resolve symbol"

- Clear build cache: `rm -rf classes .cpcache`
- Update dependencies: `clojure -P`
- Try again

### "Permission denied" when installing

- For system install: Ensure sudo is available
- Alternative: Choose user install option (2) which doesn't require sudo

### Binary works in project but not after install

- Check PATH: `echo $PATH`
- For ~/.local/bin install: Add to PATH as shown above
- Verify installation: `which proserunner`

## Development Workflow

For development, you don't need to rebuild the binary every time:

```bash
# Run tests
clojure -M:test

# Run without building
clojure -M:run -f resources/drivel.md

# Rebuild only when making changes that need testing in native binary
./tools/build.sh
```
