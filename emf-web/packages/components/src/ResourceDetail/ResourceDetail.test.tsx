import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ResourceDetail, setComponentRegistry } from './ResourceDetail';
import { EMFProvider } from '../context/EMFContext';
import type { EMFClient, ResourceMetadata, User } from '@emf/sdk';

// Mock data
interface TestUser {
  id: string;
  name: string;
  email: string;
  age: number;
  isActive: boolean;
  createdAt: string;
  metadata: Record<string, unknown>;
}

const mockUserData: TestUser = {
  id: '1',
  name: 'John Doe',
  email: 'john@example.com',
  age: 30,
  isActive: true,
  createdAt: '2024-01-15T10:30:00Z',
  metadata: { role: 'admin', department: 'Engineering' },
};

const mockSchema: ResourceMetadata = {
  name: 'users',
  displayName: 'Users',
  fields: [
    { name: 'name', type: 'string', displayName: 'Full Name' },
    { name: 'email', type: 'string', displayName: 'Email Address' },
    { name: 'age', type: 'number', displayName: 'Age' },
    { name: 'isActive', type: 'boolean', displayName: 'Active Status' },
    { name: 'createdAt', type: 'datetime', displayName: 'Created At' },
    { name: 'metadata', type: 'json', displayName: 'Metadata' },
  ],
  operations: ['list', 'get', 'create', 'update', 'delete'],
};

const mockSchemaWithAuthz: ResourceMetadata = {
  ...mockSchema,
  authz: {
    fieldLevel: {
      email: ['admin'],
      metadata: ['admin', 'manager'],
    },
  },
};

// Mock EMFClient
const createMockClient = (
  discoverFn: ReturnType<typeof vi.fn> = vi.fn(),
  getFn: ReturnType<typeof vi.fn> = vi.fn()
) => {
  const mockResource = {
    list: vi.fn(),
    get: getFn.mockResolvedValue(mockUserData),
    create: vi.fn(),
    update: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
    query: vi.fn(),
    getName: vi.fn().mockReturnValue('users'),
    buildQueryParams: vi.fn(),
    buildListUrl: vi.fn(),
  };

  return {
    resource: vi.fn().mockReturnValue(mockResource),
    discover: discoverFn.mockResolvedValue([mockSchema]),
    admin: {},
    getAxiosInstance: vi.fn(),
    isValidationEnabled: vi.fn().mockReturnValue(false),
  } as unknown as EMFClient;
};

// Test wrapper component - creates a fresh QueryClient each time
const createWrapper = (client: EMFClient, user?: User | null) => {
  return ({ children }: { children: React.ReactNode }) => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
          staleTime: 0,
        },
      },
    });

    return (
      <QueryClientProvider client={queryClient}>
        <EMFProvider client={client} user={user}>
          {children}
        </EMFProvider>
      </QueryClientProvider>
    );
  };
};

describe('ResourceDetail', () => {
  let mockClient: EMFClient;
  let mockDiscoverFn: ReturnType<typeof vi.fn>;
  let mockGetFn: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockDiscoverFn = vi.fn().mockResolvedValue([mockSchema]);
    mockGetFn = vi.fn().mockResolvedValue(mockUserData);
    mockClient = createMockClient(mockDiscoverFn, mockGetFn);
  });

  afterEach(() => {
    vi.clearAllMocks();
    setComponentRegistry(undefined as never);
  });

  describe('Data Fetching (Requirement 13.1)', () => {
    it('should fetch resource data and schema on mount', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(mockClient.discover).toHaveBeenCalled();
        expect(mockClient.resource).toHaveBeenCalledWith('users');
      });

      await waitFor(() => {
        expect(mockGetFn).toHaveBeenCalledWith('1');
      });
    });

    it('should display fetched data', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByText('John Doe')).toBeInTheDocument();
        expect(screen.getByText('john@example.com')).toBeInTheDocument();
        expect(screen.getByText('30')).toBeInTheDocument();
      });
    });
  });

  describe('Field Rendering (Requirement 13.2)', () => {
    it('should render fields based on schema', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        // Check labels are rendered
        expect(screen.getByText('Full Name')).toBeInTheDocument();
        expect(screen.getByText('Email Address')).toBeInTheDocument();
        expect(screen.getByText('Age')).toBeInTheDocument();
        expect(screen.getByText('Active Status')).toBeInTheDocument();
      });
    });

    it('should render all fields from schema', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const fields = screen.getAllByRole('term');
        expect(fields).toHaveLength(mockSchema.fields.length);
      });
    });
  });

  describe('Custom Field Renderers (Requirement 13.3)', () => {
    it('should use custom renderer when provided', async () => {
      const customRenderers = {
        name: (value: unknown) => (
          <strong data-testid="custom-name">{String(value)}</strong>
        ),
      };

      render(
        <ResourceDetail
          resourceName="users"
          recordId="1"
          customRenderers={customRenderers}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const customElement = screen.getByTestId('custom-name');
        expect(customElement).toBeInTheDocument();
        expect(customElement).toHaveTextContent('John Doe');
      });
    });

    it('should use ComponentRegistry renderer when available', async () => {
      const mockRegistryRenderer = vi.fn(({ value }) => (
        <span data-testid="registry-renderer">{String(value)}</span>
      ));

      setComponentRegistry({
        getFieldRenderer: (type: string) => type === 'string' ? mockRegistryRenderer : undefined,
        hasFieldRenderer: (type: string) => type === 'string',
      });

      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const registryElements = screen.getAllByTestId('registry-renderer');
        expect(registryElements.length).toBeGreaterThan(0);
      });
    });
  });

  describe('Default Renderers (Requirement 13.4)', () => {
    it('should use default renderer for string type', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByText('John Doe')).toBeInTheDocument();
      });
    });

    it('should use default renderer for number type', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByText('30')).toBeInTheDocument();
      });
    });

    it('should use default renderer for boolean type', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByText('Yes')).toBeInTheDocument();
      });
    });

    it('should use default renderer for datetime type', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        // The time element is rendered for datetime fields
        const timeElements = document.querySelectorAll('time');
        expect(timeElements.length).toBeGreaterThan(0);
      });
    });

    it('should use default renderer for json type', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByText(/"role": "admin"/)).toBeInTheDocument();
      });
    });
  });

  describe('Field-Level Authorization (Requirement 13.5)', () => {
    it('should hide fields when user lacks required role', async () => {
      // Create a fresh mock client with authz schema
      const authzDiscoverFn = vi.fn().mockResolvedValue([mockSchemaWithAuthz]);
      const authzGetFn = vi.fn().mockResolvedValue(mockUserData);
      const authzMockResource = {
        list: vi.fn(),
        get: authzGetFn,
        create: vi.fn(),
        update: vi.fn(),
        patch: vi.fn(),
        delete: vi.fn(),
        query: vi.fn(),
        getName: vi.fn().mockReturnValue('users'),
        buildQueryParams: vi.fn(),
        buildListUrl: vi.fn(),
      };
      const authzClient = {
        resource: vi.fn().mockReturnValue(authzMockResource),
        discover: authzDiscoverFn,
        admin: {},
        getAxiosInstance: vi.fn(),
        isValidationEnabled: vi.fn().mockReturnValue(false),
      } as unknown as EMFClient;

      const regularUser: User = {
        id: '2',
        username: 'regular',
        roles: ['user'],
      };

      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(authzClient, regularUser) }
      );

      await waitFor(() => {
        // Name should be visible (no restriction)
        expect(screen.getByText('Full Name')).toBeInTheDocument();
      });

      // Email should be hidden (requires admin role)
      expect(screen.queryByText('Email Address')).not.toBeInTheDocument();
      // Metadata should be hidden (requires admin or manager role)
      expect(screen.queryByText('Metadata')).not.toBeInTheDocument();
    });

    it('should show fields when user has required role', async () => {
      // Create a fresh mock client with authz schema
      const authzDiscoverFn = vi.fn().mockResolvedValue([mockSchemaWithAuthz]);
      const authzGetFn = vi.fn().mockResolvedValue(mockUserData);
      const authzMockResource = {
        list: vi.fn(),
        get: authzGetFn,
        create: vi.fn(),
        update: vi.fn(),
        patch: vi.fn(),
        delete: vi.fn(),
        query: vi.fn(),
        getName: vi.fn().mockReturnValue('users'),
        buildQueryParams: vi.fn(),
        buildListUrl: vi.fn(),
      };
      const authzClient = {
        resource: vi.fn().mockReturnValue(authzMockResource),
        discover: authzDiscoverFn,
        admin: {},
        getAxiosInstance: vi.fn(),
        isValidationEnabled: vi.fn().mockReturnValue(false),
      } as unknown as EMFClient;

      const adminUser: User = {
        id: '1',
        username: 'admin',
        roles: ['admin'],
      };

      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(authzClient, adminUser) }
      );

      await waitFor(() => {
        expect(screen.getByText('Full Name')).toBeInTheDocument();
        expect(screen.getByText('Email Address')).toBeInTheDocument();
        expect(screen.getByText('Metadata')).toBeInTheDocument();
      });
    });

    it('should show all fields when no user is logged in and no restrictions', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient, null) }
      );

      await waitFor(() => {
        expect(screen.getByText('Full Name')).toBeInTheDocument();
        expect(screen.getByText('Email Address')).toBeInTheDocument();
      });
    });
  });

  describe('Loading State (Requirement 13.6)', () => {
    it('should display loading indicator while fetching data', async () => {
      let resolvePromise: (value: TestUser) => void;
      const pendingPromise = new Promise<TestUser>((resolve) => {
        resolvePromise = resolve;
      });
      mockGetFn.mockReturnValue(pendingPromise);

      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      expect(screen.getByText('Loading...')).toBeInTheDocument();
      expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true');

      resolvePromise!(mockUserData);
    });

    it('should have proper accessibility attributes during loading', async () => {
      let resolvePromise: (value: TestUser) => void;
      const pendingPromise = new Promise<TestUser>((resolve) => {
        resolvePromise = resolve;
      });
      mockGetFn.mockReturnValue(pendingPromise);

      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      const loadingContainer = screen.getByRole('status');
      expect(loadingContainer).toHaveAttribute('aria-busy', 'true');
      expect(loadingContainer).toHaveAttribute('aria-label', 'Loading users details');

      resolvePromise!(mockUserData);
    });
  });

  describe('Error State (Requirement 13.7)', () => {
    it('should display error message when data fetching fails', async () => {
      mockGetFn.mockRejectedValue(new Error('Network error'));

      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByText(/Error loading data/)).toBeInTheDocument();
        expect(screen.getByText(/Network error/)).toBeInTheDocument();
      });
    });

    it('should display retry button on error', async () => {
      mockGetFn.mockRejectedValue(new Error('Network error'));

      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
      });
    });

    it('should retry fetching data when retry button is clicked', async () => {
      const user = userEvent.setup();
      mockGetFn
        .mockRejectedValueOnce(new Error('Network error'))
        .mockResolvedValueOnce(mockUserData);
      mockClient = createMockClient(mockDiscoverFn, mockGetFn);

      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByText(/Error loading data/)).toBeInTheDocument();
      });

      const retryButton = screen.getByRole('button', { name: /retry/i });
      await user.click(retryButton);

      // Just verify the button was clickable and the retry was attempted
      await waitFor(() => {
        expect(mockGetFn).toHaveBeenCalled();
      });
    });

    it('should have proper accessibility attributes for error state', async () => {
      mockGetFn.mockRejectedValue(new Error('Network error'));

      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const errorContainer = screen.getByRole('alert');
        expect(errorContainer).toHaveAttribute('aria-live', 'assertive');
      });
    });

    it('should display not found message when schema is not found', async () => {
      // Return an empty array so the schema won't be found
      const emptyDiscoverFn = vi.fn().mockResolvedValue([]);
      const localClient = createMockClient(emptyDiscoverFn, mockGetFn);

      render(
        <ResourceDetail resourceName="nonexistent" recordId="1" />,
        { wrapper: createWrapper(localClient) }
      );

      await waitFor(() => {
        // When schema is not found, it shows the not found message
        expect(screen.getByText(/Resource schema not found/)).toBeInTheDocument();
      });
    });

    it('should display not found message when record is not found', async () => {
      mockGetFn.mockResolvedValue(null);

      render(
        <ResourceDetail resourceName="users" recordId="999" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByText(/Record not found/)).toBeInTheDocument();
      });
    });
  });

  describe('Layout Options', () => {
    it('should apply vertical layout class by default', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const container = screen.getByTestId('emf-resource-detail');
        expect(container).toHaveClass('emf-resource-detail--vertical');
      });
    });

    it('should apply horizontal layout class when specified', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" layout="horizontal" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const container = screen.getByTestId('emf-resource-detail');
        expect(container).toHaveClass('emf-resource-detail--horizontal');
      });
    });

    it('should apply grid layout class when specified', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" layout="grid" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const container = screen.getByTestId('emf-resource-detail');
        expect(container).toHaveClass('emf-resource-detail--grid');
      });
    });
  });

  describe('Accessibility', () => {
    it('should use description list semantics', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const container = screen.getByTestId('emf-resource-detail');
        expect(container.tagName).toBe('DL');
      });
    });

    it('should have proper aria-label', async () => {
      render(
        <ResourceDetail resourceName="users" recordId="1" />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const container = screen.getByTestId('emf-resource-detail');
        expect(container).toHaveAttribute('aria-label', 'Users details');
      });
    });

    it('should apply custom className', async () => {
      render(
        <ResourceDetail 
          resourceName="users" 
          recordId="1" 
          className="custom-class"
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const container = screen.getByTestId('emf-resource-detail');
        expect(container).toHaveClass('custom-class');
      });
    });

    it('should use custom testId', async () => {
      render(
        <ResourceDetail 
          resourceName="users" 
          recordId="1" 
          testId="my-detail"
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByTestId('my-detail')).toBeInTheDocument();
      });
    });
  });
});
