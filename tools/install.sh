#!/bin/bash
## Exit if any command fails.
set -e

echo "=== Clerk Installation ==="
echo ""

# Build the binary
echo "Building clerk binary..."
./tools/build.sh

# Verify build succeeded
if [ ! -f "clerk" ]; then
    echo "ERROR: Build failed - clerk binary not found"
    exit 1
fi

echo ""
echo "=== Installation Options ==="
echo ""
echo "Where would you like to install clerk?"
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
        if [ -f "/usr/local/bin/clerk" ]; then
            echo "Warning: /usr/local/bin/clerk already exists"
            read -p "Overwrite? [y/N]: " overwrite
            if [[ ! $overwrite =~ ^[Yy]$ ]]; then
                echo "Installation cancelled."
                exit 0
            fi
        fi

        sudo mv clerk /usr/local/bin/clerk
        echo "✓ Installed to /usr/local/bin/clerk"
        ;;

    2)
        echo ""
        echo "Installing to ~/.local/bin..."

        # Create directory if it doesn't exist
        mkdir -p ~/.local/bin

        # Check if target already exists
        if [ -f "$HOME/.local/bin/clerk" ]; then
            echo "Warning: ~/.local/bin/clerk already exists"
            read -p "Overwrite? [y/N]: " overwrite
            if [[ ! $overwrite =~ ^[Yy]$ ]]; then
                echo "Installation cancelled."
                exit 0
            fi
        fi

        mv clerk ~/.local/bin/clerk
        echo "✓ Installed to ~/.local/bin/clerk"

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
        echo "Binary remains at: $(pwd)/clerk"
        exit 0
        ;;

    *)
        echo "Invalid choice. Installation cancelled."
        echo "Binary remains at: $(pwd)/clerk"
        exit 1
        ;;
esac

echo ""
echo "=== Installation Complete ==="
echo ""
echo "Verify installation:"
echo "  clerk --version"
echo ""
echo "Get help:"
echo "  clerk --help"
echo ""
