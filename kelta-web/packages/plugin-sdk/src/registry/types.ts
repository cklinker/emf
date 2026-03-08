import type { FieldDefinition, KeltaClient, User } from '@kelta/sdk';

/**
 * Props passed to field renderer components
 */
export interface FieldRendererProps {
  /**
   * Field value
   */
  value: unknown;

  /**
   * Field definition
   */
  field: FieldDefinition;

  /**
   * Change handler (for edit mode)
   */
  onChange?: (value: unknown) => void;

  /**
   * Whether the field is read-only
   */
  readOnly?: boolean;
}

/**
 * Field renderer component type
 */
export type FieldRendererComponent = (props: FieldRendererProps) => JSX.Element;

/**
 * Props passed to page components
 */
export interface PageComponentProps {
  /**
   * Kelta client instance
   */
  client: KeltaClient;

  /**
   * Route parameters
   */
  params: Record<string, string>;

  /**
   * Current user
   */
  user: User | null;
}

/**
 * Page component type
 */
export type PageComponent = (props: PageComponentProps) => JSX.Element;

/**
 * Registered page component with route
 */
export interface RegisteredPageComponent {
  name: string;
  route: string;
  component: PageComponent;
}
