#!/usr/bin/env python3
import re
import sys

def fix_test_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Check if already fixed
    if 'createTestWrapper' in content:
        print(f"Skipping {filepath} - already fixed")
        return
    
    lines = content.split('\n')
    new_lines = []
    in_create_wrapper = False
    brace_count = 0
    added_import = False
    added_cleanup_var = False
    in_describe_block = False
    describe_depth = 0
    
    for i, line in enumerate(lines):
        # Add import after vitest import
        if "from 'vitest'" in line and not added_import:
            new_lines.append(line)
            new_lines.append("import { createTestWrapper, setupAuthMocks } from '../../test/testUtils';")
            added_import = True
            continue
        
        # Remove old imports
        if any(x in line for x in ['BrowserRouter', 'QueryClient', 'QueryClientProvider', 'I18nProvider', 'ToastProvider']) and 'import' in line:
            continue
        
        # Skip createWrapper function - look for the function declaration
        if 'function createWrapper()' in line or 'function createTestWrapper()' in line:
            in_create_wrapper = True
            brace_count = 0
            continue
        
        if in_create_wrapper:
            # Count braces to know when function ends
            brace_count += line.count('{') - line.count('}')
            if brace_count < 0:
                in_create_wrapper = False
            continue
        
        # Replace createWrapper() with createTestWrapper()
        line = line.replace('createWrapper()', 'createTestWrapper()')
        
        # Track describe blocks
        if 'describe(' in line:
            describe_depth += 1
            if describe_depth == 1 and not added_cleanup_var:
                new_lines.append(line)
                new_lines.append('  let cleanupAuthMocks: () => void;')
                new_lines.append('')
                added_cleanup_var = True
                continue
        
        # Add setup in beforeEach ONLY at top level (describe_depth == 1)
        if '  beforeEach(() => {' in line and describe_depth == 1:
            new_lines.append(line)
            # Check if next line already has cleanup setup
            if i + 1 < len(lines) and 'cleanupAuthMocks' not in lines[i + 1]:
                new_lines.append('    cleanupAuthMocks = setupAuthMocks();')
            continue
        
        # Add cleanup in afterEach ONLY at top level
        if '  afterEach(() => {' in line and describe_depth == 1:
            new_lines.append(line)
            # Check if next line already has cleanup
            if i + 1 < len(lines) and 'cleanupAuthMocks' not in lines[i + 1]:
                new_lines.append('    cleanupAuthMocks();')
            continue
        
        new_lines.append(line)
    
    # Write back
    with open(filepath, 'w') as f:
        f.write('\n'.join(new_lines))
    
    print(f"Fixed {filepath}")

# List of files to fix
files = [
    "src/pages/CollectionsPage/CollectionsPage.test.tsx",
    "src/pages/DashboardPage/DashboardPage.test.tsx",
    "src/pages/PluginsPage/PluginsPage.test.tsx",
    "src/pages/MigrationsPage/MigrationsPage.test.tsx",
]

for filepath in files:
    try:
        fix_test_file(filepath)
    except Exception as e:
        print(f"Error fixing {filepath}: {e}")
        import traceback
        traceback.print_exc()


