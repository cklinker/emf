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
  PlatformUser,
  CreatePlatformUserRequest,
  UpdatePlatformUserRequest,
  LoginHistoryEntry,
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
  Profile,
  CreateProfileRequest,
  UpdateProfileRequest,
  ObjectPermissionRequest,
  FieldPermissionRequest,
  SystemPermissionRequest,
  PermissionSet,
  CreatePermissionSetRequest,
  UpdatePermissionSetRequest,
  OrgWideDefault,
  SetOwdRequest,
  SharingRule,
  CreateSharingRuleRequest,
  UpdateSharingRuleRequest,
  RecordShare,
  UserGroup,
  CreateUserGroupRequest,
  RoleHierarchyNode,
  SetupAuditTrailEntry,
  GovernorLimitsStatus,
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
   * User management operations
   */
  readonly users = {
    list: async (
      filter?: string,
      status?: string,
      page = 0,
      size = 20
    ): Promise<Page<PlatformUser>> => {
      const params = new URLSearchParams();
      if (filter) params.append('filter', filter);
      if (status) params.append('status', status);
      params.append('page', page.toString());
      params.append('size', size.toString());
      const response = await this.axios.get<Page<PlatformUser>>(
        `/control/users?${params.toString()}`
      );
      return response.data;
    },

    get: async (id: string): Promise<PlatformUser> => {
      const response = await this.axios.get<PlatformUser>(`/control/users/${id}`);
      return response.data;
    },

    create: async (request: CreatePlatformUserRequest): Promise<PlatformUser> => {
      const response = await this.axios.post<PlatformUser>('/control/users', request);
      return response.data;
    },

    update: async (id: string, request: UpdatePlatformUserRequest): Promise<PlatformUser> => {
      const response = await this.axios.put<PlatformUser>(`/control/users/${id}`, request);
      return response.data;
    },

    deactivate: async (id: string): Promise<void> => {
      await this.axios.post(`/control/users/${id}/deactivate`);
    },

    activate: async (id: string): Promise<void> => {
      await this.axios.post(`/control/users/${id}/activate`);
    },

    getLoginHistory: async (id: string, page = 0, size = 20): Promise<Page<LoginHistoryEntry>> => {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', size.toString());
      const response = await this.axios.get<Page<LoginHistoryEntry>>(
        `/control/users/${id}/login-history?${params.toString()}`
      );
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

  /**
   * Profile management operations
   */
  readonly profiles = {
    list: async (): Promise<Profile[]> => {
      const response = await this.axios.get<Profile[]>('/control/profiles');
      return response.data;
    },

    get: async (id: string): Promise<Profile> => {
      const response = await this.axios.get<Profile>(`/control/profiles/${id}`);
      return response.data;
    },

    create: async (request: CreateProfileRequest): Promise<Profile> => {
      const response = await this.axios.post<Profile>('/control/profiles', request);
      return response.data;
    },

    update: async (id: string, request: UpdateProfileRequest): Promise<Profile> => {
      const response = await this.axios.put<Profile>(`/control/profiles/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/profiles/${id}`);
    },

    setObjectPermissions: async (
      id: string,
      collectionId: string,
      perms: ObjectPermissionRequest
    ): Promise<void> => {
      await this.axios.put(`/control/profiles/${id}/object-permissions/${collectionId}`, perms);
    },

    setFieldPermissions: async (id: string, perms: FieldPermissionRequest[]): Promise<void> => {
      await this.axios.put(`/control/profiles/${id}/field-permissions`, perms);
    },

    setSystemPermissions: async (id: string, perms: SystemPermissionRequest[]): Promise<void> => {
      await this.axios.put(`/control/profiles/${id}/system-permissions`, perms);
    },
  };

  /**
   * Permission set management operations
   */
  readonly permissionSets = {
    list: async (): Promise<PermissionSet[]> => {
      const response = await this.axios.get<PermissionSet[]>('/control/permission-sets');
      return response.data;
    },

    get: async (id: string): Promise<PermissionSet> => {
      const response = await this.axios.get<PermissionSet>(`/control/permission-sets/${id}`);
      return response.data;
    },

    create: async (request: CreatePermissionSetRequest): Promise<PermissionSet> => {
      const response = await this.axios.post<PermissionSet>('/control/permission-sets', request);
      return response.data;
    },

    update: async (id: string, request: UpdatePermissionSetRequest): Promise<PermissionSet> => {
      const response = await this.axios.put<PermissionSet>(
        `/control/permission-sets/${id}`,
        request
      );
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/permission-sets/${id}`);
    },

    setObjectPermissions: async (
      id: string,
      collectionId: string,
      perms: ObjectPermissionRequest
    ): Promise<void> => {
      await this.axios.put(
        `/control/permission-sets/${id}/object-permissions/${collectionId}`,
        perms
      );
    },

    setFieldPermissions: async (id: string, perms: FieldPermissionRequest[]): Promise<void> => {
      await this.axios.put(`/control/permission-sets/${id}/field-permissions`, perms);
    },

    setSystemPermissions: async (id: string, perms: SystemPermissionRequest[]): Promise<void> => {
      await this.axios.put(`/control/permission-sets/${id}/system-permissions`, perms);
    },

    assign: async (id: string, userId: string): Promise<void> => {
      await this.axios.post(`/control/permission-sets/${id}/assign/${userId}`);
    },

    unassign: async (id: string, userId: string): Promise<void> => {
      await this.axios.delete(`/control/permission-sets/${id}/assign/${userId}`);
    },
  };

  /**
   * Record-level sharing operations
   */
  readonly sharing = {
    getOwd: async (collectionId: string): Promise<OrgWideDefault> => {
      const response = await this.axios.get<OrgWideDefault>(`/control/sharing/owd/${collectionId}`);
      return response.data;
    },

    setOwd: async (collectionId: string, request: SetOwdRequest): Promise<OrgWideDefault> => {
      const response = await this.axios.put<OrgWideDefault>(
        `/control/sharing/owd/${collectionId}`,
        request
      );
      return response.data;
    },

    listOwds: async (): Promise<OrgWideDefault[]> => {
      const response = await this.axios.get<OrgWideDefault[]>('/control/sharing/owd');
      return response.data;
    },

    listRules: async (collectionId: string): Promise<SharingRule[]> => {
      const response = await this.axios.get<SharingRule[]>(
        `/control/sharing/rules/${collectionId}`
      );
      return response.data;
    },

    createRule: async (
      collectionId: string,
      request: CreateSharingRuleRequest
    ): Promise<SharingRule> => {
      const response = await this.axios.post<SharingRule>(
        `/control/sharing/rules/${collectionId}`,
        request
      );
      return response.data;
    },

    updateRule: async (ruleId: string, request: UpdateSharingRuleRequest): Promise<SharingRule> => {
      const response = await this.axios.put<SharingRule>(
        `/control/sharing/rules/${ruleId}`,
        request
      );
      return response.data;
    },

    deleteRule: async (ruleId: string): Promise<void> => {
      await this.axios.delete(`/control/sharing/rules/${ruleId}`);
    },

    listRecordShares: async (collectionId: string, recordId: string): Promise<RecordShare[]> => {
      const response = await this.axios.get<RecordShare[]>(
        `/control/sharing/records/${collectionId}/${recordId}`
      );
      return response.data;
    },
  };

  /**
   * User group operations
   */
  readonly groups = {
    list: async (): Promise<UserGroup[]> => {
      const response = await this.axios.get<UserGroup[]>('/control/sharing/groups');
      return response.data;
    },

    get: async (id: string): Promise<UserGroup> => {
      const response = await this.axios.get<UserGroup>(`/control/sharing/groups/${id}`);
      return response.data;
    },

    create: async (request: CreateUserGroupRequest): Promise<UserGroup> => {
      const response = await this.axios.post<UserGroup>('/control/sharing/groups', request);
      return response.data;
    },

    updateMembers: async (id: string, memberIds: string[]): Promise<UserGroup> => {
      const response = await this.axios.put<UserGroup>(
        `/control/sharing/groups/${id}/members`,
        memberIds
      );
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/sharing/groups/${id}`);
    },
  };

  /**
   * Setup audit trail operations
   */
  readonly audit = {
    list: async (params?: {
      section?: string;
      entityType?: string;
      userId?: string;
      from?: string;
      to?: string;
      page?: number;
      size?: number;
    }): Promise<Page<SetupAuditTrailEntry>> => {
      const searchParams = new URLSearchParams();
      if (params?.section) searchParams.set('section', params.section);
      if (params?.entityType) searchParams.set('entityType', params.entityType);
      if (params?.userId) searchParams.set('userId', params.userId);
      if (params?.from) searchParams.set('from', params.from);
      if (params?.to) searchParams.set('to', params.to);
      if (params?.page !== undefined) searchParams.set('page', String(params.page));
      if (params?.size !== undefined) searchParams.set('size', String(params.size));
      const qs = searchParams.toString();
      const url = qs ? `/control/audit?${qs}` : '/control/audit';
      const response = await this.axios.get<Page<SetupAuditTrailEntry>>(url);
      return response.data;
    },

    getEntityHistory: async (
      entityType: string,
      entityId: string,
      page?: number,
      size?: number
    ): Promise<Page<SetupAuditTrailEntry>> => {
      const searchParams = new URLSearchParams();
      if (page !== undefined) searchParams.set('page', String(page));
      if (size !== undefined) searchParams.set('size', String(size));
      const qs = searchParams.toString();
      const url = qs
        ? `/control/audit/entity/${entityType}/${entityId}?${qs}`
        : `/control/audit/entity/${entityType}/${entityId}`;
      const response = await this.axios.get<Page<SetupAuditTrailEntry>>(url);
      return response.data;
    },
  };

  /**
   * Governor limits operations
   */
  readonly governorLimits = {
    getStatus: async (): Promise<GovernorLimitsStatus> => {
      const response = await this.axios.get<GovernorLimitsStatus>('/control/governor-limits');
      return response.data;
    },
  };

  /**
   * Role hierarchy operations
   */
  readonly roleHierarchy = {
    get: async (): Promise<RoleHierarchyNode[]> => {
      const response = await this.axios.get<RoleHierarchyNode[]>(
        '/control/sharing/roles/hierarchy'
      );
      return response.data;
    },

    setParent: async (roleId: string, parentRoleId: string | null): Promise<RoleHierarchyNode> => {
      const response = await this.axios.put<RoleHierarchyNode>(
        `/control/sharing/roles/${roleId}/parent`,
        parentRoleId
      );
      return response.data;
    },
  };
}
