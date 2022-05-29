## Installation

[Download](#download) | [Build from source](#build-from-source) | [Clojure CLI tools](#clojure-cli-tools)

### Download

Clerk uses GraalVM's Native Image utility to compile to a binary executable.

1. Download the [latest binary](https://github.com/jeff-bruemmer/clerk/releases) for your system. Linux and Mac OS (Darwin) binaries are available. Windows users can run Clerk on Windows Subsystem for Linux.
2. `cd` into your Downloads directory and rename download to `clerk`.
3. `chmod +x clerk`.
4. Add Clerk to your \$PATH, e.g., `sudo cp clerk /usr/local/bin`.

### Build from source

If you've installed [GraalVM](https://www.graalvm.org/) and [Native Image](https://www.graalvm.org/reference-manual/native-image/), you can build the binary yourself:

```
clj -M:build
```

Or install it with:

```
./tools/install.sh
```

### Clojure CLI tools

Run with Clojure's CLI tools.

```
clj -M:run -f /path/to/file
```
