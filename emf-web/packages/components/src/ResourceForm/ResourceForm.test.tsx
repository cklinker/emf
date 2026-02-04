import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ResourceForm, setComponentRegistry } from './ResourceForm';
import { EMFProvider } from '../context/EMFContext';
import type { EMFClient, ResourceMetadata, User } from '@emf/sdk';

// Mock schema data
const mockSchema: ResourceMetadata = {
  name: 'users',
  displayName: 'Users',
  fields: [
    { name: 'name', type: 'string', displayName: 'Name', required: true },
    { name: 'email', type: 'string', displayName: 'Email', required: true, validation: [{ type: 'email', message: 'Invalid email' }] },
    { name: 'age', type: 'number', displayName: 'Age', required: false },
    { name: 'active', type: 'boolean', displayName: 'Active', required: false },
    { name: 'birthDate', type: 'date', displayName: 'Birth Date', required: false },
    { name: 'metadata', type: 'json', displayName: 'Metadata', required: false },
  ],
  operations: ['list', 'get', 'create', 'update', 'delete'],
};

const mockSchemaWithAuthz: ResourceMetadata = {
  ...mockSchema,
  authz: {
    fieldLevel: {
      email: ['admin'],
      metadata: ['admin', 'editor'],
    },
  },
};

const mockExistingData = {
  id: '123',
  name: 'John Doe',
  email: 'john@example.com',
  age: 30,
  active: true,
  birthDate: '1993-05-15',
  metadata: '{"role": "user"}',
};

// Mock EMFClient
const createMockClient = (options: {
  discoverFn?: ReturnType<typeof vi.fn>;
  getFn?: ReturnType<typeof vi.fn>;
  createFn?: ReturnType<typeof vi.fn>;
  updateFn?: ReturnType<typeof vi.fn>;
} = {}) => {
  const mockResource = {
    list: vi.fn(),
    get: options.getFn ?? vi.fn().mockResolvedValue(mockExistingData),
    create: options.createFn ?? vi.fn().mockResolvedValue({ id: 'new-123', ...mockExistingData }),
    update: options.updateFn ?? vi.fn().mockResolvedValue(mockExistingData),
    patch: vi.fn(),
    delete: vi.fn(),
    query: vi.fn(),
    getName: vi.fn().mockReturnValue('users'),
    buildQueryParams: vi.fn(),
    buildListUrl: vi.fn(),
  };

  return {
    resource: vi.fn().mockReturnValue(mockResource),
    discover: options.discoverFn ?? vi.fn().mockResolvedValue([mockSchema]),
    admin: {},
    getAxiosInstance: vi.fn(),
    isValidationEnabled: vi.fn().mockReturnValue(false),
  } as unknown as EMFClient;
};

// Test wrapper component
const createWrapper = (client: EMFClient, user?: User | null) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <EMFProvider client={client} user={user}>
        {children}
      </EMFProvider>
    </QueryClientProvider>
  );
};

describe('ResourceForm', () => {
  let mockClient: EMFClient;
  const mockOnSave = vi.fn();
  const mockOnCancel = vi.fn();

  beforeEach(() => {
    mockClient = createMockClient();
    vi.clearAllMocks();
  });

  afterEach(() => {
    setComponentRegistry(undefined as never);
  });

  describe('Schema Fetching (Requirement 12.1)', () => {
    it('should fetch collection schema on mount', async () => {
      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(mockClient.discover).toHaveBeenCalled();
      });
    });

    it('should display loading state while fetching schema', () => {
      // Create a client that returns a pending promise
      const pendingClient = createMockClient({
        discoverFn: vi.fn().mockReturnValue(new Promise(() => {})),
      });

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(pendingClient) }
      );

      expect(screen.getByText('Loading...')).toBeInTheDocument();
      expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true');
    });

    it('should display error when schema not found', async () => {
      const emptyClient = createMockClient({
        discoverFn: vi.fn().mockResolvedValue([]),
      });

      render(
        <ResourceForm
          resourceName="nonexistent"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(emptyClient) }
      );

      await waitFor(() => {
        // The component shows an error when schema is not found
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });
  });

  describe('Field Generation (Requirement 12.2)', () => {
    it('should generate form fields from schema', async () => {
      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
        expect(screen.getByLabelText(/Email/)).toBeInTheDocument();
        expect(screen.getByLabelText(/Age/)).toBeInTheDocument();
        expect(screen.getByLabelText(/Active/)).toBeInTheDocument();
        expect(screen.getByLabelText(/Birth Date/)).toBeInTheDocument();
        expect(screen.getByLabelText(/Metadata/)).toBeInTheDocument();
      });
    });

    it('should render correct input types for each field type', async () => {
      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        // String field
        const nameInput = screen.getByLabelText(/Name/) as HTMLInputElement;
        expect(nameInput.type).toBe('text');

        // Number field
        const ageInput = screen.getByLabelText(/Age/) as HTMLInputElement;
        expect(ageInput.type).toBe('number');

        // Boolean field
        const activeInput = screen.getByLabelText(/Active/) as HTMLInputElement;
        expect(activeInput.type).toBe('checkbox');

        // Date field
        const birthDateInput = screen.getByLabelText(/Birth Date/) as HTMLInputElement;
        expect(birthDateInput.type).toBe('date');

        // JSON field (textarea)
        const metadataInput = screen.getByLabelText(/Metadata/);
        expect(metadataInput.tagName.toLowerCase()).toBe('textarea');
      });
    });

    it('should mark required fields with asterisk', async () => {
      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const nameLabel = screen.getByText('Name').closest('label');
        expect(nameLabel?.querySelector('.emf-resource-form__required')).toBeInTheDocument();

        const ageLabel = screen.getByText('Age').closest('label');
        expect(ageLabel?.querySelector('.emf-resource-form__required')).not.toBeInTheDocument();
      });
    });
  });

  describe('Edit Mode Data Fetching (Requirement 12.3)', () => {
    it('should fetch existing data when recordId is provided', async () => {
      const getFn = vi.fn().mockResolvedValue(mockExistingData);
      const clientWithGet = createMockClient({ getFn });

      render(
        <ResourceForm
          resourceName="users"
          recordId="123"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(clientWithGet) }
      );

      await waitFor(() => {
        expect(clientWithGet.resource).toHaveBeenCalledWith('users');
        expect(getFn).toHaveBeenCalledWith('123');
      });
    });

    it('should populate form with existing data in edit mode', async () => {
      const getFn = vi.fn().mockResolvedValue(mockExistingData);
      const clientWithGet = createMockClient({ getFn });

      render(
        <ResourceForm
          resourceName="users"
          recordId="123"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(clientWithGet) }
      );

      await waitFor(() => {
        const nameInput = screen.getByLabelText(/Name/) as HTMLInputElement;
        expect(nameInput.value).toBe('John Doe');

        const emailInput = screen.getByLabelText(/Email/) as HTMLInputElement;
        expect(emailInput.value).toBe('john@example.com');
      });
    });

    it('should not fetch data when recordId is not provided', async () => {
      const getFn = vi.fn();
      const clientWithGet = createMockClient({ getFn });

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(clientWithGet) }
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
      });

      expect(getFn).not.toHaveBeenCalled();
    });
  });

  describe('Form Validation (Requirement 12.4)', () => {
    it('should validate required fields', async () => {
      const user = userEvent.setup();

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
      });

      // Submit without filling required fields
      const submitButton = screen.getByRole('button', { name: /Create/i });
      await user.click(submitButton);

      // Should not call onSave due to validation errors
      expect(mockOnSave).not.toHaveBeenCalled();
    });

    it('should validate email format', async () => {
      const user = userEvent.setup();

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/Email/)).toBeInTheDocument();
      });

      // Fill in name but invalid email
      const nameInput = screen.getByLabelText(/Name/);
      const emailInput = screen.getByLabelText(/Email/);

      await user.type(nameInput, 'John Doe');
      await user.type(emailInput, 'invalid-email');

      const submitButton = screen.getByRole('button', { name: /Create/i });
      await user.click(submitButton);

      // Should show validation error
      await waitFor(() => {
        expect(screen.getByText(/Invalid email/i)).toBeInTheDocument();
      });
    });
  });

  describe('Create/Update Operation Selection (Requirement 12.5)', () => {
    it('should call create when recordId is not provided', async () => {
      const user = userEvent.setup();
      const createFn = vi.fn().mockResolvedValue({ id: 'new-123', name: 'John Doe', email: 'john@example.com' });
      const clientWithCreate = createMockClient({ createFn });

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(clientWithCreate) }
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
      });

      // Fill in required fields
      await user.type(screen.getByLabelText(/Name/), 'John Doe');
      await user.type(screen.getByLabelText(/Email/), 'john@example.com');

      // Submit
      const submitButton = screen.getByRole('button', { name: /Create/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(createFn).toHaveBeenCalled();
      });
    });

    it('should call update when recordId is provided', async () => {
      const user = userEvent.setup();
      const updateFn = vi.fn().mockResolvedValue(mockExistingData);
      const getFn = vi.fn().mockResolvedValue(mockExistingData);
      const clientWithUpdate = createMockClient({ updateFn, getFn });

      render(
        <ResourceForm
          resourceName="users"
          recordId="123"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(clientWithUpdate) }
      );

      await waitFor(() => {
        const nameInput = screen.getByLabelText(/Name/) as HTMLInputElement;
        expect(nameInput.value).toBe('John Doe');
      });

      // Modify a field
      const nameInput = screen.getByLabelText(/Name/);
      await user.clear(nameInput);
      await user.type(nameInput, 'Jane Doe');

      // Submit
      const submitButton = screen.getByRole('button', { name: /Update/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(updateFn).toHaveBeenCalled();
      });
    });

    it('should show "Create" button in create mode', async () => {
      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Create/i })).toBeInTheDocument();
      });
    });

    it('should show "Update" button in edit mode', async () => {
      const getFn = vi.fn().mockResolvedValue(mockExistingData);
      const clientWithGet = createMockClient({ getFn });

      render(
        <ResourceForm
          resourceName="users"
          recordId="123"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(clientWithGet) }
      );

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Update/i })).toBeInTheDocument();
      });
    });
  });

  describe('Callbacks (Requirement 12.6, 12.7)', () => {
    it('should call onSave callback after successful save', async () => {
      const user = userEvent.setup();
      const createFn = vi.fn().mockResolvedValue({ id: 'new-123', name: 'John Doe', email: 'john@example.com' });
      const clientWithCreate = createMockClient({ createFn });

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(clientWithCreate) }
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
      });

      await user.type(screen.getByLabelText(/Name/), 'John Doe');
      await user.type(screen.getByLabelText(/Email/), 'john@example.com');

      const submitButton = screen.getByRole('button', { name: /Create/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(mockOnSave).toHaveBeenCalledWith(expect.objectContaining({ id: 'new-123' }));
      });
    });

    it('should call onCancel callback when cancel button is clicked', async () => {
      const user = userEvent.setup();

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
      });

      const cancelButton = screen.getByRole('button', { name: /Cancel/i });
      await user.click(cancelButton);

      expect(mockOnCancel).toHaveBeenCalled();
    });
  });

  describe('Field-Level Authorization (Requirement 12.8)', () => {
    it('should hide fields when user lacks required role', async () => {
      const authzClient = createMockClient({
        discoverFn: vi.fn().mockResolvedValue([mockSchemaWithAuthz]),
      });

      const regularUser: User = {
        id: 'user-1',
        username: 'regular',
        roles: ['user'],
      };

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(authzClient, regularUser) }
      );

      await waitFor(() => {
        // Name should be visible (no role restriction)
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
        
        // Email should be hidden (requires admin role)
        expect(screen.queryByLabelText(/Email/)).not.toBeInTheDocument();
        
        // Metadata should be hidden (requires admin or editor role)
        expect(screen.queryByLabelText(/Metadata/)).not.toBeInTheDocument();
      });
    });

    it('should show fields when user has required role', async () => {
      const authzClient = createMockClient({
        discoverFn: vi.fn().mockResolvedValue([mockSchemaWithAuthz]),
      });

      const adminUser: User = {
        id: 'admin-1',
        username: 'admin',
        roles: ['admin'],
      };

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(authzClient, adminUser) }
      );

      await waitFor(() => {
        // All fields should be visible for admin
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
        expect(screen.getByLabelText(/Email/)).toBeInTheDocument();
        expect(screen.getByLabelText(/Metadata/)).toBeInTheDocument();
      });
    });

    it('should show fields when user has one of the required roles', async () => {
      const authzClient = createMockClient({
        discoverFn: vi.fn().mockResolvedValue([mockSchemaWithAuthz]),
      });

      const editorUser: User = {
        id: 'editor-1',
        username: 'editor',
        roles: ['editor'],
      };

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(authzClient, editorUser) }
      );

      await waitFor(() => {
        // Name should be visible (no role restriction)
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
        
        // Email should be hidden (requires admin role only)
        expect(screen.queryByLabelText(/Email/)).not.toBeInTheDocument();
        
        // Metadata should be visible (editor is in the allowed roles)
        expect(screen.getByLabelText(/Metadata/)).toBeInTheDocument();
      });
    });
  });

  describe('Read-Only Mode', () => {
    it('should disable all inputs when readOnly is true', async () => {
      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
          readOnly={true}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const nameInput = screen.getByLabelText(/Name/) as HTMLInputElement;
        expect(nameInput.disabled).toBe(true);

        const emailInput = screen.getByLabelText(/Email/) as HTMLInputElement;
        expect(emailInput.disabled).toBe(true);
      });
    });

    it('should hide submit button when readOnly is true', async () => {
      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
          readOnly={true}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
      });

      expect(screen.queryByRole('button', { name: /Create/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Update/i })).not.toBeInTheDocument();
    });

    it('should still show cancel button when readOnly is true', async () => {
      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
          readOnly={true}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Cancel/i })).toBeInTheDocument();
      });
    });
  });

  describe('Initial Values', () => {
    it('should populate form with initial values', async () => {
      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
          initialValues={{ name: 'Initial Name', email: 'initial@example.com' }}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        const nameInput = screen.getByLabelText(/Name/) as HTMLInputElement;
        expect(nameInput.value).toBe('Initial Name');

        const emailInput = screen.getByLabelText(/Email/) as HTMLInputElement;
        expect(emailInput.value).toBe('initial@example.com');
      });
    });
  });

  describe('Custom Field Renderers', () => {
    it('should use custom field renderer when registered', async () => {
      const CustomStringRenderer = vi.fn(({ value, onChange, readOnly }) => (
        <input
          data-testid="custom-string-input"
          type="text"
          value={value as string ?? ''}
          onChange={(e) => onChange?.(e.target.value)}
          disabled={readOnly}
        />
      ));

      setComponentRegistry({
        hasFieldRenderer: (type: string) => type === 'string',
        getFieldRenderer: (type: string) => type === 'string' ? CustomStringRenderer : undefined,
      });

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(mockClient) }
      );

      await waitFor(() => {
        // Custom renderer should be used for string fields
        const customInputs = screen.getAllByTestId('custom-string-input');
        expect(customInputs.length).toBeGreaterThan(0);
      });
    });
  });

  describe('Error Handling', () => {
    it('should display error when save fails', async () => {
      const user = userEvent.setup();
      const saveError = new Error('Save failed');
      const createFn = vi.fn().mockRejectedValue(saveError);
      const clientWithError = createMockClient({ createFn });

      render(
        <ResourceForm
          resourceName="users"
          onSave={mockOnSave}
          onCancel={mockOnCancel}
        />,
        { wrapper: createWrapper(clientWithError) }
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/Name/)).toBeInTheDocument();
      });

      await user.type(screen.getByLabelText(/Name/), 'John Doe');
      await user.type(screen.getByLabelText(/Email/), 'john@example.com');

      const submitButton = screen.getByRole('button', { name: /Create/i });
      await user.click(submitButton);

      // Wait for the error to be displayed
      await waitFor(() => {
        const errorBanner = screen.queryByRole('alert');
        expect(errorBanner).toBeInTheDocument();
      }, { timeout: 2000 });
    });
  });
});
