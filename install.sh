#!/usr/bin/env bash
# Proserunner installation script

set -e

echo "=== Proserunner Installer ==="
echo ""

# Check for Java
if ! command -v java &> /dev/null; then
    echo "Java not found."
    echo ""
    echo "Install Java:"
    echo "  macOS:  brew install openjdk"
    echo "  Linux:  sudo apt install openjdk-11-jdk  # or equivalent"
    echo ""
    exit 1
fi

echo "✓ Java found: $(java -version 2>&1 | head -1)"

# Check for Clojure
if ! command -v clojure &> /dev/null; then
    echo "✗ Clojure not found"
    echo ""
    echo "Install Clojure:"
    echo "  macOS:  brew install clojure/tools/clojure"
    echo "  Linux:  curl -L https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh | bash"
    echo ""
    exit 1
fi

echo "✓ Clojure found: $(clojure --version)"
echo ""

# Create ~/.local/bin if needed
mkdir -p ~/.local/bin

# Get absolute path to proserunner
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROSERUNNER_PATH="$SCRIPT_DIR/proserunner"
TARGET="$HOME/.local/bin/proserunner"

# Warn if overwriting existing installation
if [ -L "$TARGET" ] || [ -f "$TARGET" ]; then
    echo "Overwriting existing installation at $TARGET"
    echo ""
fi

# Create symlink (force overwrite if exists)
ln -sf "$PROSERUNNER_PATH" "$TARGET"
echo "✓ Installed to $TARGET"
echo ""

# Check PATH
if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
    echo "⚠ ~/.local/bin is not in your PATH"
    echo ""
    echo "Add this to your shell config (~/.bashrc, ~/.zshrc, etc.):"
    echo '  export PATH="$HOME/.local/bin:$PATH"'
    echo ""
    echo "Then run:"
    echo "  source ~/.bashrc"
    echo ""
else
    echo "✓ ~/.local/bin is in PATH"
    echo ""
fi

# Test
echo "Testing installation..."
if "$TARGET" --version &> /dev/null; then
    echo "✓ Installation successful!"
    echo ""
    "$TARGET" --version
else
    echo "✗ Installation test failed"
    exit 1
fi
