#!/bin/bash

# Find all test files and remove duplicate setupAuthMocks calls in nested beforeEach blocks
for file in src/pages/*/*.test.tsx src/components/*/*.test.tsx; do
  if [ -f "$file" ]; then
    # Remove lines that have setupAuthMocks in nested beforeEach (with extra indentation)
    # Keep only the first one at the top level
    perl -i -pe 's/^    cleanupAuthMocks = setupAuthMocks\(\);\n//' "$file"
  fi
done

echo "Removed duplicate setupAuthMocks calls"
