# Installation

## Native binary (recommended)

Build and install the native binary for fastest startup (~50ms):

```bash
git clone https://github.com/jeff-bruemmer/proserunner.git
cd proserunner
bb install  # Builds and installs to ~/.local/bin
```

**Requirements:**

- Babashka
- GraalVM 25+ with native-image
- Minimum 8GB RAM

See [building.md](building.md) for detailed setup instructions.

**System-wide installation**

```bash
bb install-system  # Installs to /usr/local/bin (requires sudo)
```

## Run without installing

Use Clojure CLI directly:

```bash
git clone https://github.com/jeff-bruemmer/proserunner.git
cd proserunner
clojure -M:run -f /path/to/file.md
```

**Requirements:**

- Java
- Clojure CLI

## Pre-built Binaries

Download from [releases](https://github.com/jeff-bruemmer/proserunner/releases), make executable, move to PATH.
