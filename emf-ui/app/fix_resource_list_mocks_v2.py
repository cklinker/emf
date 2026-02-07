#!/usr/bin/env python3
"""
Fix ResourceListPage test mocks to properly handle all API endpoints
"""

import re

# Read the file
with open('src/pages/ResourceListPage/ResourceListPage.test.tsx', 'r') as f:
    content = f.read()

# Define the correct mock implementation that handles all three endpoints
correct_mock_impl = '''const mockFetch = vi.fn((input: unknown) => {
        const url = getUrlFromFetchArg(input);
        // Handle /control/collections (list all collections)
        if (url.includes('/control/collections') && !url.match(/\\/control\\/collections\\/[^?]/)) {
          return Promise.resolve(createMockResponse({ content: [mockSchema], totalElements: 1, totalPages: 1, size: 1000, number: 0 }));
        }
        // Handle /control/collections/{id} (get single collection)
        if (url.match(/\\/control\\/collections\\/col-1/)) {
          return Promise.resolve(createMockResponse(mockSchema));
        }
        // Handle /api/users (get resources)
        if (url.includes('/api/users')) {
          return Promise.resolve(createMockResponse(mockPaginatedResponse));
        }
        return Promise.reject(new Error('Unknown URL: ' + url));
      });
      wrapFetchMock(mockFetch);'''

# Pattern to match the existing mock implementations in beforeEach blocks
# This matches from "const mockFetch = vi.fn(" to the "wrapFetchMock(mockFetch);"
pattern = r'const mockFetch = vi\.fn\(\(input: unknown\) => \{[^}]+\}[^}]+\}[^}]+\}\);[\s\n]+wrapFetchMock\(mockFetch\);'

# Replace all occurrences
content = re.sub(pattern, correct_mock_impl, content, flags=re.MULTILINE | re.DOTALL)

# Write back
with open('src/pages/ResourceListPage/ResourceListPage.test.tsx', 'w') as f:
    f.write(content)

print("Fixed ResourceListPage test mocks")
