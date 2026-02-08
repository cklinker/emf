/**
 * Utility Functions
 *
 * This module exports all utility functions used throughout the application.
 * Utilities include: formatting, validation helpers, storage helpers, etc.
 */

// Route guard utilities
export {
  checkPageAuthorization,
  checkRoleAuthorization,
  checkPolicyAuthorization,
  checkAuthorization,
  getRedirectPath,
  filterAuthorizedPages,
  canAccessRoute,
} from './routeGuards'
export type { AuthorizationResult } from './routeGuards'

// Export utility functions as they are implemented
// Example exports:
// export { formatDate, formatNumber, formatCurrency } from './formatters';
// export { validateEmail, validateUrl, validatePattern } from './validators';
// export { getFromStorage, setToStorage, removeFromStorage } from './storage';
// export { debounce, throttle } from './timing';
// export { classNames, mergeStyles } from './styles';
