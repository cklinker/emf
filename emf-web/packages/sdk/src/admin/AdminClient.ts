import type { AxiosInstance } from 'axios';
import type {
  CollectionDefinition,
  FieldDefinition,
  Role,
  Policy,
  OIDCProvider,
  UIConfig,
  PackageData,
  ExportOptions,
  ImportResult,
  Migration,
  UIPage,
  UIMenu,
  MigrationPlan,
  MigrationRun,
  Tenant,
  CreateTenantRequest,
  UpdateTenantRequest,
  GovernorLimits,
  Page,
} from './types';

/**
 * Admin client for control plane operations
 */
export class AdminClient {
  constructor(private readonly axios: AxiosInstance) {}

  /**
   * Collection management operations
   */
  readonly collections = {
    list: async (): Promise<CollectionDefinition[]> => {
      const response = await this.axios.get<CollectionDefinition[]>('/control/collections');
      return response.data;
    },

    get: async (id: string): Promise<CollectionDefinition> => {
      const response = await this.axios.get<CollectionDefinition>(`/control/collections/${id}`);
      return response.data;
    },

    create: async (definition: CollectionDefinition): Promise<CollectionDefinition> => {
      const response = await this.axios.post<CollectionDefinition>(
        '/control/collections',
        definition
      );
      return response.data;
    },

    update: async (id: string, definition: CollectionDefinition): Promise<CollectionDefinition> => {
      const response = await this.axios.put<CollectionDefinition>(
        `/control/collections/${id}`,
        definition
      );
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/collections/${id}`);
    },
  };

  /**
   * Field management operations
   */
  readonly fields = {
    add: async (collectionId: string, field: FieldDefinition): Promise<FieldDefinition> => {
      const response = await this.axios.post<FieldDefinition>(
        `/control/collections/${collectionId}/fields`,
        field
      );
      return response.data;
    },

    update: async (
      collectionId: string,
      fieldId: string,
      field: FieldDefinition
    ): Promise<FieldDefinition> => {
      const response = await this.axios.put<FieldDefinition>(
        `/control/collections/${collectionId}/fields/${fieldId}`,
        field
      );
      return response.data;
    },

    delete: async (collectionId: string, fieldId: string): Promise<void> => {
      await this.axios.delete(`/control/collections/${collectionId}/fields/${fieldId}`);
    },
  };

  /**
   * Authorization management operations
   */
  readonly authz = {
    listRoles: async (): Promise<Role[]> => {
      const response = await this.axios.get<Role[]>('/control/roles');
      return response.data;
    },

    createRole: async (role: Role): Promise<Role> => {
      const response = await this.axios.post<Role>('/control/roles', role);
      return response.data;
    },

    updateRole: async (id: string, role: Role): Promise<Role> => {
      const response = await this.axios.put<Role>(`/control/roles/${id}`, role);
      return response.data;
    },

    deleteRole: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/roles/${id}`);
    },

    listPolicies: async (): Promise<Policy[]> => {
      const response = await this.axios.get<Policy[]>('/control/policies');
      return response.data;
    },

    createPolicy: async (policy: Policy): Promise<Policy> => {
      const response = await this.axios.post<Policy>('/control/policies', policy);
      return response.data;
    },

    updatePolicy: async (id: string, policy: Policy): Promise<Policy> => {
      const response = await this.axios.put<Policy>(`/control/policies/${id}`, policy);
      return response.data;
    },

    deletePolicy: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/policies/${id}`);
    },
  };

  /**
   * OIDC provider management operations
   */
  readonly oidc = {
    list: async (): Promise<OIDCProvider[]> => {
      const response = await this.axios.get<OIDCProvider[]>('/control/oidc/providers');
      return response.data;
    },

    get: async (id: string): Promise<OIDCProvider> => {
      const response = await this.axios.get<OIDCProvider>(`/control/oidc/providers/${id}`);
      return response.data;
    },

    create: async (provider: OIDCProvider): Promise<OIDCProvider> => {
      const response = await this.axios.post<OIDCProvider>('/control/oidc/providers', provider);
      return response.data;
    },

    update: async (id: string, provider: OIDCProvider): Promise<OIDCProvider> => {
      const response = await this.axios.put<OIDCProvider>(
        `/control/oidc/providers/${id}`,
        provider
      );
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/oidc/providers/${id}`);
    },
  };

  /**
   * UI configuration operations
   */
  readonly ui = {
    getBootstrap: async (): Promise<UIConfig> => {
      const response = await this.axios.get<UIConfig>('/ui/config/bootstrap');
      return response.data;
    },

    listPages: async (): Promise<UIPage[]> => {
      const response = await this.axios.get<UIPage[]>('/ui/pages');
      return response.data;
    },

    createPage: async (page: UIPage): Promise<UIPage> => {
      const response = await this.axios.post<UIPage>('/ui/pages', page);
      return response.data;
    },

    updatePage: async (id: string, page: UIPage): Promise<UIPage> => {
      const response = await this.axios.put<UIPage>(`/ui/pages/${id}`, page);
      return response.data;
    },

    listMenus: async (): Promise<UIMenu[]> => {
      const response = await this.axios.get<UIMenu[]>('/ui/menus');
      return response.data;
    },

    updateMenu: async (id: string, menu: UIMenu): Promise<UIMenu> => {
      const response = await this.axios.put<UIMenu>(`/ui/menus/${id}`, menu);
      return response.data;
    },
  };

  /**
   * Package import/export operations
   */
  readonly packages = {
    export: async (options: ExportOptions): Promise<PackageData> => {
      const response = await this.axios.post<PackageData>('/control/packages/export', options);
      return response.data;
    },

    import: async (packageData: PackageData): Promise<ImportResult> => {
      const response = await this.axios.post<ImportResult>('/control/packages/import', packageData);
      return response.data;
    },
  };

  /**
   * Tenant management operations (platform admin)
   */
  readonly tenants = {
    list: async (page = 0, size = 20): Promise<Page<Tenant>> => {
      const response = await this.axios.get<Page<Tenant>>(
        `/platform/tenants?page=${page}&size=${size}`
      );
      return response.data;
    },

    get: async (id: string): Promise<Tenant> => {
      const response = await this.axios.get<Tenant>(`/platform/tenants/${id}`);
      return response.data;
    },

    create: async (request: CreateTenantRequest): Promise<Tenant> => {
      const response = await this.axios.post<Tenant>('/platform/tenants', request);
      return response.data;
    },

    update: async (id: string, request: UpdateTenantRequest): Promise<Tenant> => {
      const response = await this.axios.put<Tenant>(`/platform/tenants/${id}`, request);
      return response.data;
    },

    suspend: async (id: string): Promise<void> => {
      await this.axios.post(`/platform/tenants/${id}/suspend`);
    },

    activate: async (id: string): Promise<void> => {
      await this.axios.post(`/platform/tenants/${id}/activate`);
    },

    getLimits: async (id: string): Promise<GovernorLimits> => {
      const response = await this.axios.get<GovernorLimits>(`/platform/tenants/${id}/limits`);
      return response.data;
    },
  };

  /**
   * Migration operations
   */
  readonly migrations = {
    plan: async (
      collectionId: string,
      targetSchema: CollectionDefinition
    ): Promise<MigrationPlan> => {
      const response = await this.axios.post<MigrationPlan>('/control/migrations/plan', {
        collectionId,
        targetSchema,
      });
      return response.data;
    },

    listRuns: async (): Promise<Migration[]> => {
      const response = await this.axios.get<Migration[]>('/control/migrations/runs');
      return response.data;
    },

    getRun: async (id: string): Promise<MigrationRun> => {
      const response = await this.axios.get<MigrationRun>(`/control/migrations/runs/${id}`);
      return response.data;
    },
  };
}
