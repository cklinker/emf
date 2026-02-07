#!/bin/bash

# Fix PackagesPage
sed -i '' '/^\/\*\*/,/^}$/{ 
  /^\/\*\*/,/^  return function Wrapper/d
  /^  return function Wrapper/,/^}$/d
}' src/pages/PackagesPage/PackagesPage.test.tsx

# Fix PoliciesPage  
sed -i '' '/^\/\*\*/,/^}$/{ 
  /^\/\*\*/,/^  return function Wrapper/d
  /^  return function Wrapper/,/^}$/d
}' src/pages/PoliciesPage/PoliciesPage.test.tsx

# Fix ResourceBrowserPage
sed -i '' '/^\/\*\*/,/^}$/{ 
  /^\/\*\*/,/^  return function Wrapper/d
  /^  return function Wrapper/,/^}$/d
}' src/pages/ResourceBrowserPage/ResourceBrowserPage.test.tsx

# Fix RolesPage
sed -i '' '/^\/\*\*/,/^}$/{ 
  /^\/\*\*/,/^  return function Wrapper/d
  /^  return function Wrapper/,/^}$/d
}' src/pages/RolesPage/RolesPage.test.tsx

