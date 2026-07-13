#!/bin/bash
# ============================================
# run.sh - Production Build & Run
# Builds frontend + backend, then starts backend
# serving both from port 8080
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=== Data Analysis Agent - Production ===${NC}"

# Load .env if exists
if [ -f .env ]; then
    echo -e "${GREEN}Loading .env...${NC}"
    export $(grep -v '^#' .env | xargs)
fi

# Parse arguments
SKIP_BUILD=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build) SKIP_BUILD=true; shift ;;
        --help|-h)
            echo "Usage: ./run.sh [--skip-build]"
            echo "  --skip-build  Skip compilation step"
            exit 0
            ;;
        *) shift ;;
    esac
done

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java not found. Please install JDK 17+.${NC}"
    exit 1
fi

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven not found. Please install Maven 3.8+.${NC}"
    exit 1
fi

# Build
if [ "$SKIP_BUILD" = false ]; then
    # Install frontend deps
    echo -e "${GREEN}Installing frontend dependencies...${NC}"
    cd frontend && npm install && cd ..

    # Build frontend
    echo -e "${GREEN}Building frontend...${NC}"
    cd frontend && npm run build && cd ..

    # Copy frontend dist to backend static resources
    echo -e "${GREEN}Copying frontend assets...${NC}"
    mkdir -p src/main/resources/static
    cp -r frontend/dist/* src/main/resources/static/

    # Build backend (fat jar)
    echo -e "${GREEN}Building backend...${NC}"
    mvn clean package -DskipTests -q

    echo -e "${GREEN}Build complete!${NC}"
fi

# Run
echo -e "${GREEN}Starting application...${NC}"
echo -e "${BLUE}Access: http://localhost:8080${NC}"
echo ""
java -jar target/data-analysis-agent-1.0.0.jar
