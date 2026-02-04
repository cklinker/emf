/**
 * AuthorizationPanel Component
 *
 * Exports the AuthorizationPanel component and related types for managing
 * route-level and field-level authorization configuration.
 */

export { AuthorizationPanel, ROUTE_OPERATIONS, FIELD_OPERATIONS } from './AuthorizationPanel';
export type {
  AuthorizationPanelProps,
  RouteOperation,
  FieldOperation,
  PolicySummary,
  RoutePolicyConfig,
  FieldPolicyConfig,
  FieldDefinition,
  CollectionAuthz,
} from './AuthorizationPanel';
