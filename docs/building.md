# Building from source

## Prerequisites

- Clojure CLI: https://clojure.org/guides/install_clojure
- GraalVM 21 with native-image

### GraalVM install

**Option 1: Download from Oracle**

```bash
cd ~
mkdir graalvm && cd graalvm
curl -L -o graalvm.tar.gz https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz
tar -xzf graalvm.tar.gz
export JAVA_HOME=~/graalvm/graalvm-jdk-21.0.9+7.1
export PATH=$JAVA_HOME/bin:$PATH
```

**Option 2: SDKMAN**

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21-graal
```

## Build and install

```bash
./tools/build.sh    # 30-60 seconds, ~60MB binary
./tools/install.sh  # Choose: system-wide, user-only, or cancel
```

See [tools.md](tools.md) for detailed script documentation.

## Run without building

```bash
clojure -M:run -f /path/to/file.md
```
