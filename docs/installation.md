# Installation

## Download binary

1. Download [latest release](https://github.com/jeff-bruemmer/proserunner/releases) (Linux, macOS, WSL)
2. Make executable: `chmod +x proserunner`
3. Move to PATH: `sudo cp proserunner /usr/local/bin`

## Build from source

```bash
git clone https://github.com/jeff-bruemmer/proserunner.git
cd proserunner
./tools/install.sh
```

See [building.md](building.md) for prerequisites and [tools.md](tools.md) for script details.

## Run with Clojure

```bash
clj -M:run -f /path/to/file
```
