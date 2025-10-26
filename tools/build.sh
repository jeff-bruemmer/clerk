#!/bin/bash
## Exit if any command fails.
set -e

echo "Initializing build..."

## Check prerequisites
echo "Checking prerequisites..."

# Check for clojure
if ! command -v clojure &> /dev/null; then
    echo "ERROR: clojure command not found."
    echo "Please install Clojure CLI tools: https://clojure.org/guides/install_clojure"
    exit 1
fi

# Check for native-image
if ! command -v native-image &> /dev/null; then
    echo "ERROR: native-image command not found."
    echo ""
    echo "GraalVM native-image is required to build the native binary."
    echo ""
    echo "Installation options:"
    echo "  1. Download GraalVM: https://www.graalvm.org/downloads/"
    echo "  2. Or use SDKMAN: sdk install java 21-graal"
    echo ""
    echo "After installing GraalVM, make sure native-image is in your PATH:"
    echo "  export JAVA_HOME=/path/to/graalvm"
    echo "  export PATH=\$JAVA_HOME/bin:\$PATH"
    echo ""
    echo "Alternatively, run 'clojure -M:run' to use Clerk without building a native binary."
    exit 1
fi

echo "✓ Prerequisites found"
echo "  Java: $(java -version 2>&1 | head -1)"
echo "  Clojure: $(clojure --version 2>&1)"

## Remove existing classes directory and clerk binary

echo "Cleaning build artifacts..."
rm -rf classes .cpcache

echo "Building native image..."
echo "This could take 30-60 seconds..."
echo ""

clojure -M:build

# Verify the binary was created
if [ ! -f "clerk" ]; then
    echo "ERROR: Build failed - clerk binary not found"
    echo "Check the output above for errors."
    exit 1
fi

echo ""
echo "Making the clerk image executable..."
chmod +x clerk

echo "Cleaning up build artifacts..."
rm -rf classes .cpcache

echo ""
echo "✓ Build successful!"
echo ""
echo "Testing binary..."
./clerk -h

echo ""
echo "Binary location: $(pwd)/clerk"
echo "Binary size: $(du -h clerk | cut -f1)"


