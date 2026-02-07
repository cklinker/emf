#!/usr/bin/env python3
"""
Fix mockFetch patterns in ResourceListPage.test.tsx
Converts mockFetch.mockImplementation to const mockFetch = vi.fn() + wrapFetchMock()
"""

import re

# Read the file
with open('src/pages/ResourceListPage/ResourceListPage.test.tsx', 'r') as f:
    content = f.read()

# Pattern 1: Fix beforeEach blocks with mockFetch.mockImplementation
# Find all beforeEach blocks that use mockFetch.mockImplementation
pattern1 = r'(beforeEach\(\(\) => \{\s+)mockFetch\.mockImplementation\('
replacement1 = r'\1const mockFetch = vi.fn('

content = re.sub(pattern1, replacement1, content)

# Pattern 2: Add wrapFetchMock after the closing of the mockFetch function
# This is tricky - we need to find the end of the vi.fn() call and add wrapFetchMock
# Look for patterns like:  });  followed by next line with });
pattern2 = r'(const mockFetch = vi\.fn\([^;]+\);)\s+(\}\);)'
replacement2 = r'\1\n      wrapFetchMock(mockFetch);\n    \2'

content = re.sub(pattern2, replacement2, content, flags=re.MULTILINE | re.DOTALL)

# Pattern 3: Fix the standalone mockFetch.mockImplementation in the "display empty state" test
pattern3 = r'(\s+it\([^{]+\{\s+)mockFetch\.mockImplementation\('
replacement3 = r'\1const mockFetch = vi.fn('

content = re.sub(pattern3, replacement3, content)

# Pattern 4: Add wrapFetchMock after standalone mockFetch definitions in tests
# Look for const mockFetch = vi.fn(...); within test bodies (not beforeEach)
pattern4 = r'(it\([^{]+\{[^}]*const mockFetch = vi\.fn\([^;]+\);)(\s+renderWithProviders)'
replacement4 = r'\1\n      wrapFetchMock(mockFetch);\2'

content = re.sub(pattern4, replacement4, content, flags=re.MULTILINE | re.DOTALL)

# Write the file back
with open('src/pages/ResourceListPage/ResourceListPage.test.tsx', 'w') as f:
    f.write(content)

print("Fixed mockFetch patterns in ResourceListPage.test.tsx")
