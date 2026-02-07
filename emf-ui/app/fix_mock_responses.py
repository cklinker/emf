#!/usr/bin/env python3
"""
Script to fix mock responses in test files to return paginated responses.
Many tests are returning arrays directly when components expect { content: [...] } structure.
"""

import os
import re

# Test files that need paginated response fixes
test_files = [
    'emf-ui/app/src/pages/DashboardPage/DashboardPage.test.tsx',
    'emf-ui/app/src/pages/MenuBuilderPage/MenuBuilderPage.test.tsx',
    'emf-ui/app/src/pages/OIDCProvidersPage/OIDCProvidersPage.test.tsx',
    'emf-ui/app/src/pages/PageBuilderPage/PageBuilderPage.test.tsx',
    'emf-ui/app/src/pages/PoliciesPage/PoliciesPage.test.tsx',
    'emf-ui/app/src/pages/ResourceBrowserPage/ResourceBrowserPage.test.tsx',
    'emf-ui/app/src/pages/ResourceListPage/ResourceListPage.test.tsx',
    'emf-ui/app/src/pages/RolesPage/RolesPage.test.tsx',
]

def wrap_array_in_paginated_response(content):
    """
    Find patterns like:
    - mockFetch.mockResolvedValue(createMockResponse(mockArray))
    - mockFetch.mockImplementation(() => Promise.resolve(createMockResponse(mockArray)))
    
    And wrap mockArray with pagination structure:
    { content: mockArray, totalElements: mockArray.length, totalPages: 1, size: 1000, number: 0 }
    """
    
    # Pattern 1: mockFetch.mockResolvedValue(createMockResponse(arrayName))
    # But NOT if it already has { content: ... }
    pattern1 = r'mockFetch\.mockResolvedValue\(createMockResponse\(([a-zA-Z_][a-zA-Z0-9_]*)\)\)'
    
    def replace_simple(match):
        array_name = match.group(1)
        # Skip if it's null, undefined, or already an object literal
        if array_name in ['null', 'undefined'] or array_name.startswith('{'):
            return match.group(0)
        return f'mockFetch.mockResolvedValue(createMockResponse({{ content: {array_name}, totalElements: {array_name}.length, totalPages: 1, size: 1000, number: 0 }}))'
    
    content = re.sub(pattern1, replace_simple, content)
    
    # Pattern 2: createMockResponse(arrayName) in Promise.resolve
    pattern2 = r'Promise\.resolve\(createMockResponse\(([a-zA-Z_][a-zA-Z0-9_]*)\)\)'
    
    def replace_promise(match):
        array_name = match.group(1)
        if array_name in ['null', 'undefined'] or array_name.startswith('{'):
            return match.group(0)
        return f'Promise.resolve(createMockResponse({{ content: {array_name}, totalElements: {array_name}.length, totalPages: 1, size: 1000, number: 0 }}))'
    
    content = re.sub(pattern2, replace_promise, content)
    
    return content

def update_test_file(filepath):
    """Update a test file to fix mock responses."""
    if not os.path.exists(filepath):
        print(f"⚠️  File not found: {filepath}")
        return False
    
    with open(filepath, 'r') as f:
        content = f.read()
    
    original_content = content
    
    # Apply the transformation
    content = wrap_array_in_paginated_response(content)
    
    if content != original_content:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"✓ Updated: {filepath}")
        return True
    else:
        print(f"⚠️  No changes needed: {filepath}")
        return False

def main():
    """Update all test files."""
    print("Fixing mock responses to return paginated data...\n")
    
    updated_count = 0
    for test_file in test_files:
        if update_test_file(test_file):
            updated_count += 1
    
    print(f"\n✓ Updated {updated_count} files")

if __name__ == '__main__':
    main()
