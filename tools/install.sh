#!/bin/bash
## Exit if any command fails.
set -e

echo "=== Proserunner Installation ==="
echo ""

# Build the binary
echo "Building proserunner binary..."
./tools/build.sh

# Verify build succeeded
if [ ! -f "proserunner" ]; then
    echo "ERROR: Build failed - proserunner binary not found"
    exit 1
fi

echo ""
echo "=== Installation Options ==="
echo ""
echo "Where would you like to install proserunner?"
echo "  1) /usr/local/bin (system-wide, requires sudo)"
echo "  2) ~/.local/bin (user only, no sudo required)"
echo "  3) Cancel installation"
echo ""
read -p "Enter choice [1-3]: " choice

case $choice in
    1)
        echo ""
        echo "Installing to /usr/local/bin (requires sudo)..."

        # Check if target already exists
        if [ -f "/usr/local/bin/proserunner" ]; then
            echo "Warning: /usr/local/bin/proserunner already exists"
            read -p "Overwrite? [y/N]: " overwrite
            if [[ ! $overwrite =~ ^[Yy]$ ]]; then
                echo "Installation cancelled."
                exit 0
            fi
        fi

        sudo mv proserunner /usr/local/bin/proserunner
        echo "✓ Installed to /usr/local/bin/proserunner"
        ;;

    2)
        echo ""
        echo "Installing to ~/.local/bin..."

        # Create directory if it doesn't exist
        mkdir -p ~/.local/bin

        # Check if target already exists
        if [ -f "$HOME/.local/bin/proserunner" ]; then
            echo "Warning: ~/.local/bin/proserunner already exists"
            read -p "Overwrite? [y/N]: " overwrite
            if [[ ! $overwrite =~ ^[Yy]$ ]]; then
                echo "Installation cancelled."
                exit 0
            fi
        fi

        mv proserunner ~/.local/bin/proserunner
        echo "✓ Installed to ~/.local/bin/proserunner"

        # Check if ~/.local/bin is in PATH
        if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
            echo ""
            echo "NOTE: ~/.local/bin is not in your PATH."
            echo "Add this to your ~/.bashrc or ~/.zshrc:"
            echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
            echo ""
            echo "Then run: source ~/.bashrc"
        fi
        ;;

    3)
        echo "Installation cancelled."
        echo "Binary remains at: $(pwd)/proserunner"
        exit 0
        ;;

    *)
        echo "Invalid choice. Installation cancelled."
        echo "Binary remains at: $(pwd)/proserunner"
        exit 1
        ;;
esac

echo ""
echo "=== Installation Complete ==="
echo ""
echo "Verify installation:"
echo "  proserunner --version"
echo ""
echo "Get help:"
echo "  proserunner --help"
echo ""
