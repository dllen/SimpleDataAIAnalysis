#!/bin/bash
# ============================================
# dev.sh - Local Development Script
# Starts backend and frontend concurrently
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}=== Data Analysis Agent - Dev Mode ===${NC}"

# Load .env if exists
if [ -f .env ]; then
    echo -e "${GREEN}Loading .env...${NC}"
    export $(grep -v '^#' .env | xargs)
fi

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${YELLOW}Error: Java not found. Please install JDK 17+.${NC}"
    exit 1
fi

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${YELLOW}Error: Maven not found. Please install Maven 3.8+.${NC}"
    exit 1
fi

# Check Node.js
if ! command -v node &> /dev/null; then
    echo -e "${YELLOW}Error: Node.js not found. Please install Node.js 18+.${NC}"
    exit 1
fi

# Install frontend deps if needed
if [ ! -d "frontend/node_modules" ]; then
    echo -e "${GREEN}Installing frontend dependencies...${NC}"
    cd frontend && npm install && cd ..
fi

# Start backend
echo -e "${GREEN}Starting backend (port 8080)...${NC}"
mvn spring-boot:run -q > /tmp/agent-backend.log 2>&1 &
BACKEND_PID=$!
echo -e "${BLUE}Backend PID: $BACKEND_PID${NC}"

# Wait for backend to start
echo "Waiting for backend to start..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/api/models > /dev/null 2>&1; then
        echo -e "${GREEN}Backend started!${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${YELLOW}Backend failed to start. Check /tmp/agent-backend.log${NC}"
        kill $BACKEND_PID 2>/dev/null
        exit 1
    fi
    sleep 1
done

# Start frontend
echo -e "${GREEN}Starting frontend (port 3000)...${NC}"
cd frontend && npx vite --host > /tmp/agent-frontend.log 2>&1 &
FRONTEND_PID=$!
echo -e "${BLUE}Frontend PID: $FRONTEND_PID${NC}"

# Trap Ctrl+C to kill both processes
trap "echo -e '\n${YELLOW}Shutting down...${NC}'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit 0" INT TERM

echo ""
echo -e "${GREEN}=== Services Running ===${NC}"
echo -e "  Frontend:  ${BLUE}http://localhost:3000${NC}"
echo -e "  Backend:   ${BLUE}http://localhost:8080${NC}"
echo -e "  H2 Console: ${BLUE}http://localhost:8080/h2-console${NC}"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo ""

# Wait for both processes
wait
