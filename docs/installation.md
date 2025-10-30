## Installation

[Download](#download) | [Build from source](#build-from-source) | [Clojure CLI tools](#clojure-cli-tools)

### Download

1. Download the [latest binary](https://github.com/jeff-bruemmer/proserunner/releases) for your system (Linux, macOS, WSL for Windows).
2. Rename to `proserunner` and make executable: `chmod +x proserunner`
3. Move to PATH: `sudo cp proserunner /usr/local/bin`

### Build from source

```bash
git clone https://github.com/jeff-bruemmer/proserunner.git
cd proserunner
./tools/install.sh
```

See [Building from Source](building.md) for prerequisites and troubleshooting.

### Clojure CLI tools

```
clj -M:run -f /path/to/file
```
