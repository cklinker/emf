#!/usr/bin/env python3
"""
Script to add setupAuthMocks() calls to test files that declare cleanupAuthMocks
but don't initialize it.
"""

import os
import re

# Test files that need fixing
test_files = [
    'emf-ui/app/src/pages/MenuBuilderPage/MenuBuilderPage.test.tsx',
    'emf-ui/app/src/pages/OIDCProvidersPage/OIDCProvidersPage.test.tsx',
    'emf-ui/app/src/pages/PackagesPage/PackagesPage.test.tsx',
    'emf-ui/app/src/pages/PoliciesPage/PoliciesPage.test.tsx',
    'emf-ui/app/src/pages/ResourceBrowserPage/ResourceBrowserPage.test.tsx',
    'emf-ui/app/src/pages/ResourceDetailPage/ResourceDetailPage.test.tsx',
    'emf-ui/app/src/pages/ResourceFormPage/ResourceFormPage.test.tsx',
    'emf-ui/app/src/pages/ResourceListPage/ResourceListPage.test.tsx',
    'emf-ui/app/src/pages/RolesPage/RolesPage.test.tsx',
]

def fix_test_file(filepath):
    """Add setupAuthMocks() call to test file."""
    if not os.path.exists(filepath):
        print(f"⚠️  File not found: {filepath}")
        return False
    
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Check if already has setupAuthMocks call
    if 'cleanupAuthMocks = setupAuthMocks()' in content:
        print(f"✓ Already fixed: {filepath}")
        return False
    
    # Check if has cleanupAuthMocks declaration
    if 'let cleanupAuthMocks' not in content:
        print(f"⚠️  No cleanupAuthMocks declaration: {filepath}")
        return False
    
    original_content = content
    
    # Pattern 1: beforeEach with mockFetch.mockReset()
    pattern1 = r'(beforeEach\(\(\) => \{)\s*(\n\s*)(mockFetch\.mockReset\(\);)'
    replacement1 = r'\1\2cleanupAuthMocks = setupAuthMocks();\2\3'
    content = re.sub(pattern1, replacement1, content)
    
    # Pattern 2: beforeEach with vi.clearAllMocks() or global.fetch = vi.fn()
    if content == original_content:
        pattern2 = r'(beforeEach\(\(\) => \{)\s*(\n\s*)(vi\.clearAllMocks\(\);|global\.fetch = vi\.fn\(\);)'
        replacement2 = r'\1\2cleanupAuthMocks = setupAuthMocks();\2\3'
        content = re.sub(pattern2, replacement2, content)
    
    # Pattern 3: beforeEach with setupMswHandlers()
    if content == original_content:
        pattern3 = r'(beforeEach\(\(\) => \{)\s*(\n\s*)(vi\.clearAllMocks\(\);\s*\n\s*setupMswHandlers\(\);)'
        replacement3 = r'\1\2cleanupAuthMocks = setupAuthMocks();\2\3'
        content = re.sub(pattern3, replacement3, content)
    
    if content != original_content:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"✓ Fixed: {filepath}")
        return True
    else:
        print(f"⚠️  Pattern not found: {filepath}")
        return False

def main():
    """Fix all test files."""
    print("Adding setupAuthMocks() calls to test files...\n")
    
    fixed_count = 0
    for test_file in test_files:
        if fix_test_file(test_file):
            fixed_count += 1
    
    print(f"\n✓ Fixed {fixed_count} files")

if __name__ == '__main__':
    main()
