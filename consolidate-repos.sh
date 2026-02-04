#!/bin/bash
set -e

echo "=== EMF Monorepo Consolidation Script ==="
echo ""
echo "This script will:"
echo "1. Remove individual .git directories from subdirectories"
echo "2. Initialize a single git repository at the root"
echo "3. Create a comprehensive .gitignore"
echo "4. Add and commit all files"
echo "5. Set up the remote and push to GitHub"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

# Step 1: Remove individual .git directories
echo ""
echo "Step 1: Removing individual .git directories..."
for dir in emf-platform emfctl emf-web emf-control-plane emf-docs emf-gateway emf-helm emf-ui; do
    if [ -d "$dir/.git" ]; then
        echo "  Removing $dir/.git"
        rm -rf "$dir/.git"
    fi
done

# Also check for sample-service (not in the original list but might have .git)
if [ -d "sample-service/.git" ]; then
    echo "  Removing sample-service/.git"
    rm -rf "sample-service/.git"
fi

echo "✓ Individual .git directories removed"

# Step 2: Initialize git repository
echo ""
echo "Step 2: Initializing git repository..."
git init
echo "✓ Git repository initialized"

# Step 3: Create comprehensive .gitignore (append to existing)
echo ""
echo "Step 3: Updating .gitignore..."
cat >> .gitignore << 'EOF'

# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties
dependency-reduced-pom.xml
buildNumber.properties
.mvn/timing.properties
.mvn/wrapper/maven-wrapper.jar

# Node
node_modules/
npm-debug.log*
yarn-debug.log*
yarn-error.log*
dist/
build/
.npm
.eslintcache

# Java
*.class
*.log
*.jar
*.war
*.ear
*.zip
*.tar.gz
*.rar
hs_err_pid*

# Coverage
coverage/
*.lcov
.nyc_output

# Build artifacts
*.tsbuildinfo

# Helm
*.tgz

# Test databases
*.jqwik-database

EOF
echo "✓ .gitignore updated"

# Step 4: Add all files
echo ""
echo "Step 4: Adding all files to git..."
git add .
echo "✓ Files added"

# Step 5: Create initial commit
echo ""
echo "Step 5: Creating initial commit..."
git commit -m "Initial monorepo consolidation

Consolidated the following repositories into a single monorepo:
- emf-platform: Core backend framework
- emf-control-plane: Control plane service
- emf-ui: React admin/builder UI
- emf-gateway: API Gateway service
- emf-web: TypeScript SDK + React components
- emfctl: CLI for promotion/migrations
- emf-helm: Infrastructure templates
- emf-docs: Documentation site
- sample-service: Sample service implementation
- scripts: Development and testing scripts

This consolidation maintains all existing code and structure while
unifying version control into a single repository."
echo "✓ Initial commit created"

# Step 6: Set up remote
echo ""
echo "Step 6: Setting up GitHub remote..."
git remote add origin git@github.com:cklinker/emf.git
echo "✓ Remote added"

# Step 7: Create main branch and push
echo ""
echo "Step 7: Preparing to push to GitHub..."
git branch -M main
echo ""
echo "Ready to push to git@github.com:cklinker/emf.git"
echo ""
echo "Run the following command to push:"
echo "  git push -u origin main"
echo ""
echo "If the repository already exists on GitHub with content, you may need:"
echo "  git push -u origin main --force"
echo ""
echo "✓ Consolidation complete!"
