#!/bin/bash

# Script to toggle between hasRole('ADMIN') and isAuthenticated() in controllers
# Usage: ./toggle-admin-role-check.sh [enable|disable]

set -e

MODE=${1:-disable}
CONTROLLERS_DIR="emf-control-plane/app/src/main/java/com/emf/controlplane/controller"

if [ "$MODE" = "disable" ]; then
    echo "Disabling ADMIN role checks (replacing with isAuthenticated)..."
    find "$CONTROLLERS_DIR" -name "*.java" -type f -exec sed -i.bak \
        's/@PreAuthorize("hasRole('\''ADMIN'\'')")/@PreAuthorize("isAuthenticated()")/g' {} \;
    echo "✓ ADMIN role checks disabled"
    echo "⚠️  WARNING: Any authenticated user can now perform admin actions!"
    
elif [ "$MODE" = "enable" ]; then
    echo "Enabling ADMIN role checks (replacing isAuthenticated with hasRole('ADMIN'))..."
    find "$CONTROLLERS_DIR" -name "*.java" -type f -exec sed -i.bak \
        's/@PreAuthorize("isAuthenticated()")/@PreAuthorize("hasRole('\''ADMIN'\'')")/g' {} \;
    echo "✓ ADMIN role checks enabled"
    echo "✓ Proper role-based authorization restored"
    
else
    echo "Usage: $0 [enable|disable]"
    echo "  enable  - Restore hasRole('ADMIN') checks"
    echo "  disable - Replace with isAuthenticated() (development only)"
    exit 1
fi

# Clean up backup files
find "$CONTROLLERS_DIR" -name "*.java.bak" -type f -delete

echo ""
echo "Modified files:"
git -C emf-control-plane diff --name-only | grep "Controller.java" || echo "No changes detected"
