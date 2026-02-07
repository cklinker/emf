#!/usr/bin/env python3
"""
Script to add wrapFetchMock() calls to test files that use mockFetch.
This ensures bootstrap config requests are handled even when tests mock fetch.
"""

import os
import re

# Test files that need to be updated (relative to emf-ui/app/)
test_files = [
    'emf-ui/app/src/pages/CollectionsPage/CollectionsPage.test.tsx',
    'emf-ui/app/src/pages/DashboardPage/DashboardPage.test.tsx',
    'emf-ui/app/src/pages/MenuBuilderPage/MenuBuilderPage.test.tsx',
    'emf-ui/app/src/pages/MigrationsPage/MigrationsPage.test.tsx',
    'emf-ui/app/src/pages/OIDCProvidersPage/OIDCProvidersPage.test.tsx',
    'emf-ui/app/src/pages/PackagesPage/PackagesPage.test.tsx',
    'emf-ui/app/src/pages/PageBuilderPage/PageBuilderPage.test.tsx',
    'emf-ui/app/src/pages/PluginsPage/PluginsPage.test.tsx',
    'emf-ui/app/src/pages/PoliciesPage/PoliciesPage.test.tsx',
    'emf-ui/app/src/pages/ResourceBrowserPage/ResourceBrowserPage.test.tsx',
    'emf-ui/app/src/pages/ResourceDetailPage/ResourceDetailPage.test.tsx',
    'emf-ui/app/src/pages/ResourceFormPage/ResourceFormPage.test.tsx',
    'emf-ui/app/src/pages/ResourceListPage/ResourceListPage.test.tsx',
    'emf-ui/app/src/pages/RolesPage/RolesPage.test.tsx',
    'emf-ui/app/src/components/CollectionForm/CollectionForm.test.tsx',
]

def update_test_file(filepath):
    """Update a test file to add wrapFetchMock() call."""
    if not os.path.exists(filepath):
        print(f"⚠️  File not found: {filepath}")
        return False
    
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Check if file already has wrapFetchMock
    if 'wrapFetchMock' in content:
        print(f"✓ Already updated: {filepath}")
        return False
    
    # Check if file uses mockFetch
    if 'mockFetch' not in content:
        print(f"⚠️  No mockFetch found: {filepath}")
        return False
    
    # Check if file imports setupAuthMocks
    if 'setupAuthMocks' not in content:
        print(f"⚠️  No setupAuthMocks import: {filepath}")
        return False
    
    original_content = content
    
    # Add wrapFetchMock to imports
    import_pattern = r'(import\s+{[^}]*setupAuthMocks[^}]*})'
    def add_wrap_to_import(match):
        import_line = match.group(1)
        if 'wrapFetchMock' not in import_line:
            # Add wrapFetchMock to the import
            return import_line.replace('setupAuthMocks', 'setupAuthMocks, wrapFetchMock')
        return import_line
    
    content = re.sub(import_pattern, add_wrap_to_import, content)
    
    # Find beforeEach blocks that have mockFetch.mockReset()
    # Pattern: beforeEach(() => { ... mockFetch.mockReset(); ... })
    # We want to add wrapFetchMock(mockFetch); right after mockFetch.mockReset();
    
    # This is tricky because we need to handle nested beforeEach blocks
    # Strategy: Find all mockFetch.mockReset() calls and add wrapFetchMock after them
    
    def add_wrap_after_reset(match):
        reset_line = match.group(0)
        # Check if wrapFetchMock is already on the next line
        return reset_line + '\n    wrapFetchMock(mockFetch);'
    
    # Pattern to match mockFetch.mockReset() that doesn't already have wrapFetchMock after it
    pattern = r'mockFetch\.mockReset\(\);(?!\s*wrapFetchMock)'
    content = re.sub(pattern, add_wrap_after_reset, content)
    
    if content != original_content:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"✓ Updated: {filepath}")
        return True
    else:
        print(f"⚠️  No changes made: {filepath}")
        return False

def main():
    """Update all test files."""
    print("Updating test files to use wrapFetchMock()...\n")
    
    updated_count = 0
    for test_file in test_files:
        if update_test_file(test_file):
            updated_count += 1
    
    print(f"\n✓ Updated {updated_count} files")

if __name__ == '__main__':
    main()
