#!/usr/bin/env python3
"""Fix ResourceFormPage test file by replacing malformed fetch mock patterns."""

import re

# Read the file
with open('src/pages/ResourceFormPage/ResourceFormPage.test.tsx', 'r') as f:
    content = f.read()

# Pattern 1: Replace "global.fetch = vi.fn().mockImplementation" followed by "wrapFetchMock(vi);"
# with "const mockFetch = vi.fn().mockImplementation" followed by proper wrapFetchMock call
lines = content.split('\n')
fixed_lines = []
i = 0

while i < len(lines):
    line = lines[i]
    
    # Check if this line has the malformed pattern
    if 'global.fetch = vi.fn().mockImplementation' in line:
        # Check if next line has wrapFetchMock(vi)
        if i + 1 < len(lines) and 'wrapFetchMock(vi);' in lines[i + 1]:
            # Replace global.fetch with const mockFetch
            fixed_line = line.replace('global.fetch = vi.fn()', 'const mockFetch = vi.fn()')
            fixed_lines.append(fixed_line)
            
            # Skip the wrapFetchMock(vi) line - we'll add it at the end of the block
            i += 2
            
            # Now we need to find the end of this mockImplementation block and add wrapFetchMock there
            # Continue adding lines until we find the closing of the function
            brace_count = 0
            found_opening = False
            block_lines = []
            
            while i < len(lines):
                current = lines[i]
                block_lines.append(current)
                
                # Count braces to find the end
                if '{' in current:
                    brace_count += current.count('{')
                    found_opening = True
                if '}' in current:
                    brace_count -= current.count('}')
                
                # Check if we've closed the mockImplementation
                if found_opening and brace_count == 0 and 'as typeof global.fetch' in current:
                    # Add the block
                    fixed_lines.extend(block_lines)
                    # Add wrapFetchMock call
                    # Get the indentation from the line
                    indent = len(current) - len(current.lstrip())
                    fixed_lines.append(' ' * indent + 'wrapFetchMock(mockFetch);')
                    fixed_lines.append('')
                    i += 1
                    break
                
                i += 1
            continue
    
    fixed_lines.append(line)
    i += 1

# Write back
with open('src/pages/ResourceFormPage/ResourceFormPage.test.tsx', 'w') as f:
    f.write('\n'.join(fixed_lines))

print("Fixed ResourceFormPage test file")
