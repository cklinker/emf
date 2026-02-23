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
  GlobalPicklist,
  PicklistValue,
  PicklistDependency,
  CreateGlobalPicklistRequest,
  PicklistValueRequest,
  SetDependencyRequest,
  CollectionRelationships,
  CollectionValidationRule,
  CreateCollectionValidationRuleRequest,
  CollectionValidationError,
  RecordType,
  CreateRecordTypeRequest,
  RecordTypePicklistOverride,
  SetPicklistOverrideRequest,
  FieldHistoryEntry,
  PageLayout,
  CreatePageLayoutRequest,
  LayoutAssignment,
  LayoutAssignmentRequest,
  ListView,
  CreateListViewRequest,
  Report,
  ReportFolder,
  CreateReportRequest,
  UserDashboard,
  CreateDashboardRequest,
  ExportRequest,
  EmailTemplate,
  EmailLog,
  CreateEmailTemplateRequest,
  WorkflowRule,
  WorkflowExecutionLog,
  CreateWorkflowRuleRequest,
  ApprovalProcess,
  ApprovalInstance,
  CreateApprovalProcessRequest,
  FlowDefinition,
  FlowExecution,
  CreateFlowRequest,
  ScheduledJob,
  JobExecutionLog,
  CreateScheduledJobRequest,
  Script,
  ScriptExecutionLog,
  CreateScriptRequest,
  Webhook,
  WebhookDelivery,
  CreateWebhookRequest,
  ConnectedApp,
  ConnectedAppCreatedResponse,
  ConnectedAppToken,
  CreateConnectedAppRequest,
  BulkJob,
  BulkJobResult,
  CreateBulkJobRequest,
  CompositeRequest,
  CompositeResponse,
} from './types';
import { toJsonApiBody, unwrapJsonApiResource, unwrapJsonApiList } from './jsonapi-helpers';

/**
 * Admin client for control plane operations.
 *
 * Sections whose dedicated controllers were removed now route CRUD through
 * the worker's DynamicCollectionRouter via `/api/{collection}` (JSON:API),
 * while actions route through the generic CollectionActionController at
 * `/control/{collection}/{id}/actions/{action}`.
 *
 * Sections whose controllers still exist (collections, fields, users,
 * tenants, profiles, permissionSets, sharing, audit, etc.) remain on
 * `/control/` unchanged.
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
   * OIDC provider management operations.
   * Routed via /api/oidc-providers (JSON:API, worker).
   */
  readonly oidc = {
    list: async (): Promise<OIDCProvider[]> => {
      const response = await this.axios.get('/api/oidc-providers');
      return unwrapJsonApiList<OIDCProvider>(response.data);
    },

    get: async (id: string): Promise<OIDCProvider> => {
      const response = await this.axios.get(`/api/oidc-providers/${id}`);
      return unwrapJsonApiResource<OIDCProvider>(response.data);
    },

    create: async (provider: OIDCProvider): Promise<OIDCProvider> => {
      const body = toJsonApiBody('oidc-providers', provider as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/oidc-providers', body);
      return unwrapJsonApiResource<OIDCProvider>(response.data);
    },

    update: async (id: string, provider: OIDCProvider): Promise<OIDCProvider> => {
      const body = toJsonApiBody(
        'oidc-providers',
        provider as unknown as Record<string, unknown>,
        id
      );
      const response = await this.axios.patch(`/api/oidc-providers/${id}`, body);
      return unwrapJsonApiResource<OIDCProvider>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/oidc-providers/${id}`);
    },
  };

  /**
   * UI configuration operations
   */
  readonly ui = {
    getBootstrap: async (): Promise<UIConfig> => {
      const response = await this.axios.get<UIConfig>('/control/ui-bootstrap');
      return response.data;
    },

    listPages: async (): Promise<UIPage[]> => {
      const response = await this.axios.get<UIPage[]>('/control/ui/pages');
      return response.data;
    },

    createPage: async (page: UIPage): Promise<UIPage> => {
      const response = await this.axios.post<UIPage>('/control/ui/pages', page);
      return response.data;
    },

    updatePage: async (id: string, page: UIPage): Promise<UIPage> => {
      const response = await this.axios.put<UIPage>(`/control/ui/pages/${id}`, page);
      return response.data;
    },

    listMenus: async (): Promise<UIMenu[]> => {
      const response = await this.axios.get<UIMenu[]>('/control/ui/menus');
      return response.data;
    },

    updateMenu: async (id: string, menu: UIMenu): Promise<UIMenu> => {
      const response = await this.axios.put<UIMenu>(`/control/ui/menus/${id}`, menu);
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
        `/control/tenants?page=${page}&size=${size}`
      );
      return response.data;
    },

    get: async (id: string): Promise<Tenant> => {
      const response = await this.axios.get<Tenant>(`/control/tenants/${id}`);
      return response.data;
    },

    create: async (request: CreateTenantRequest): Promise<Tenant> => {
      const response = await this.axios.post<Tenant>('/control/tenants', request);
      return response.data;
    },

    update: async (id: string, request: UpdateTenantRequest): Promise<Tenant> => {
      const response = await this.axios.put<Tenant>(`/control/tenants/${id}`, request);
      return response.data;
    },

    suspend: async (id: string): Promise<void> => {
      await this.axios.post(`/control/tenants/${id}/suspend`);
    },

    activate: async (id: string): Promise<void> => {
      await this.axios.post(`/control/tenants/${id}/activate`);
    },

    getLimits: async (id: string): Promise<GovernorLimits> => {
      const response = await this.axios.get<GovernorLimits>(`/control/tenants/${id}/limits`);
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
   * Picklist management operations.
   * Global picklist CRUD routed via /api/global-picklists (JSON:API, worker).
   * Global picklist values via sub-resource /api/global-picklists/{id}/picklist-values.
   * Field-level picklist operations remain on /control/ (TODO: need dedicated handler).
   */
  readonly picklists = {
    listGlobal: async (_tenantId?: string): Promise<GlobalPicklist[]> => {
      const response = await this.axios.get('/api/global-picklists');
      return unwrapJsonApiList<GlobalPicklist>(response.data);
    },

    createGlobal: async (
      request: CreateGlobalPicklistRequest,
      _tenantId?: string
    ): Promise<GlobalPicklist> => {
      const body = toJsonApiBody('global-picklists', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/global-picklists', body);
      return unwrapJsonApiResource<GlobalPicklist>(response.data);
    },

    getGlobal: async (id: string): Promise<GlobalPicklist> => {
      const response = await this.axios.get(`/api/global-picklists/${id}`);
      return unwrapJsonApiResource<GlobalPicklist>(response.data);
    },

    updateGlobal: async (
      id: string,
      request: Partial<CreateGlobalPicklistRequest>
    ): Promise<GlobalPicklist> => {
      const body = toJsonApiBody(
        'global-picklists',
        request as unknown as Record<string, unknown>,
        id
      );
      const response = await this.axios.patch(`/api/global-picklists/${id}`, body);
      return unwrapJsonApiResource<GlobalPicklist>(response.data);
    },

    deleteGlobal: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/global-picklists/${id}`);
    },

    getGlobalValues: async (id: string): Promise<PicklistValue[]> => {
      const response = await this.axios.get(`/api/global-picklists/${id}/picklist-values`);
      return unwrapJsonApiList<PicklistValue>(response.data);
    },

    setGlobalValues: async (
      id: string,
      values: PicklistValueRequest[]
    ): Promise<PicklistValue[]> => {
      // Replace all values: delete existing, then create new ones via sub-resource
      const existingResponse = await this.axios.get(`/api/global-picklists/${id}/picklist-values`);
      const existing = unwrapJsonApiList<PicklistValue>(existingResponse.data);
      await Promise.all(
        existing.map((v) =>
          this.axios.delete(`/api/global-picklists/${id}/picklist-values/${v.id}`)
        )
      );
      const created = await Promise.all(
        values.map((v) => {
          const body = toJsonApiBody('picklist-values', v as unknown as Record<string, unknown>);
          return this.axios.post(`/api/global-picklists/${id}/picklist-values`, body);
        })
      );
      return created.map((r) => unwrapJsonApiResource<PicklistValue>(r.data));
    },

    // TODO: Field-level picklist operations need a dedicated controller or action handler.
    // The PicklistController was removed. These remain on /control/ paths (will 404).
    getFieldValues: async (fieldId: string): Promise<PicklistValue[]> => {
      const response = await this.axios.get<PicklistValue[]>(
        `/control/picklists/fields/${fieldId}/values`
      );
      return response.data;
    },

    setFieldValues: async (
      fieldId: string,
      values: PicklistValueRequest[]
    ): Promise<PicklistValue[]> => {
      const response = await this.axios.put<PicklistValue[]>(
        `/control/picklists/fields/${fieldId}/values`,
        values
      );
      return response.data;
    },

    getDependencies: async (fieldId: string): Promise<PicklistDependency[]> => {
      const response = await this.axios.get<PicklistDependency[]>(
        `/control/picklists/fields/${fieldId}/dependencies`
      );
      return response.data;
    },

    setDependency: async (request: SetDependencyRequest): Promise<PicklistDependency> => {
      const response = await this.axios.put<PicklistDependency>(
        '/control/picklists/dependencies',
        request
      );
      return response.data;
    },

    removeDependency: async (
      controllingFieldId: string,
      dependentFieldId: string
    ): Promise<void> => {
      await this.axios.delete(
        `/control/picklists/dependencies/${controllingFieldId}/${dependentFieldId}`
      );
    },
  };

  /**
   * Relationship operations
   */
  readonly relationships = {
    getForCollection: async (collectionId: string): Promise<CollectionRelationships> => {
      const response = await this.axios.get<CollectionRelationships>(
        `/control/collections/${collectionId}/relationships`
      );
      return response.data;
    },
  };

  /**
   * Validation rule operations
   */
  readonly validationRules = {
    list: async (collectionId: string): Promise<CollectionValidationRule[]> => {
      const response = await this.axios.get<CollectionValidationRule[]>(
        `/control/collections/${collectionId}/validation-rules`
      );
      return response.data;
    },

    create: async (
      collectionId: string,
      request: CreateCollectionValidationRuleRequest
    ): Promise<CollectionValidationRule> => {
      const response = await this.axios.post<CollectionValidationRule>(
        `/control/collections/${collectionId}/validation-rules`,
        request
      );
      return response.data;
    },

    get: async (collectionId: string, ruleId: string): Promise<CollectionValidationRule> => {
      const response = await this.axios.get<CollectionValidationRule>(
        `/control/collections/${collectionId}/validation-rules/${ruleId}`
      );
      return response.data;
    },

    update: async (
      collectionId: string,
      ruleId: string,
      request: Partial<CreateCollectionValidationRuleRequest> & { active?: boolean }
    ): Promise<CollectionValidationRule> => {
      const response = await this.axios.put<CollectionValidationRule>(
        `/control/collections/${collectionId}/validation-rules/${ruleId}`,
        request
      );
      return response.data;
    },

    delete: async (collectionId: string, ruleId: string): Promise<void> => {
      await this.axios.delete(`/control/collections/${collectionId}/validation-rules/${ruleId}`);
    },

    activate: async (collectionId: string, ruleId: string): Promise<void> => {
      await this.axios.post(
        `/control/collections/${collectionId}/validation-rules/${ruleId}/activate`
      );
    },

    deactivate: async (collectionId: string, ruleId: string): Promise<void> => {
      await this.axios.post(
        `/control/collections/${collectionId}/validation-rules/${ruleId}/deactivate`
      );
    },

    test: async (
      collectionId: string,
      testRecord: Record<string, unknown>
    ): Promise<CollectionValidationError[]> => {
      const response = await this.axios.post<CollectionValidationError[]>(
        `/control/collections/${collectionId}/validation-rules/test`,
        testRecord
      );
      return response.data;
    },
  };

  /**
   * Record type operations
   */
  readonly recordTypes = {
    list: async (collectionId: string): Promise<RecordType[]> => {
      const response = await this.axios.get<RecordType[]>(
        `/control/collections/${collectionId}/record-types`
      );
      return response.data;
    },

    create: async (collectionId: string, request: CreateRecordTypeRequest): Promise<RecordType> => {
      const response = await this.axios.post<RecordType>(
        `/control/collections/${collectionId}/record-types`,
        request
      );
      return response.data;
    },

    get: async (collectionId: string, recordTypeId: string): Promise<RecordType> => {
      const response = await this.axios.get<RecordType>(
        `/control/collections/${collectionId}/record-types/${recordTypeId}`
      );
      return response.data;
    },

    update: async (
      collectionId: string,
      recordTypeId: string,
      request: Partial<CreateRecordTypeRequest> & { active?: boolean }
    ): Promise<RecordType> => {
      const response = await this.axios.put<RecordType>(
        `/control/collections/${collectionId}/record-types/${recordTypeId}`,
        request
      );
      return response.data;
    },

    delete: async (collectionId: string, recordTypeId: string): Promise<void> => {
      await this.axios.delete(`/control/collections/${collectionId}/record-types/${recordTypeId}`);
    },

    getPicklistOverrides: async (
      collectionId: string,
      recordTypeId: string
    ): Promise<RecordTypePicklistOverride[]> => {
      const response = await this.axios.get<RecordTypePicklistOverride[]>(
        `/control/collections/${collectionId}/record-types/${recordTypeId}/picklists`
      );
      return response.data;
    },

    setPicklistOverride: async (
      collectionId: string,
      recordTypeId: string,
      fieldId: string,
      request: SetPicklistOverrideRequest
    ): Promise<RecordTypePicklistOverride> => {
      const response = await this.axios.put<RecordTypePicklistOverride>(
        `/control/collections/${collectionId}/record-types/${recordTypeId}/picklists/${fieldId}`,
        request
      );
      return response.data;
    },

    removePicklistOverride: async (
      collectionId: string,
      recordTypeId: string,
      fieldId: string
    ): Promise<void> => {
      await this.axios.delete(
        `/control/collections/${collectionId}/record-types/${recordTypeId}/picklists/${fieldId}`
      );
    },
  };

  /**
   * Field history operations
   */
  readonly fieldHistory = {
    getRecordHistory: async (
      collectionId: string,
      recordId: string,
      page?: number,
      size?: number
    ): Promise<Page<FieldHistoryEntry>> => {
      const params = new URLSearchParams();
      if (page !== undefined) params.append('page', String(page));
      if (size !== undefined) params.append('size', String(size));
      const query = params.toString();
      const response = await this.axios.get<Page<FieldHistoryEntry>>(
        `/control/collections/${collectionId}/records/${recordId}/history${query ? `?${query}` : ''}`
      );
      return response.data;
    },

    getFieldHistory: async (
      collectionId: string,
      recordId: string,
      fieldName: string,
      page?: number,
      size?: number
    ): Promise<Page<FieldHistoryEntry>> => {
      const params = new URLSearchParams();
      if (page !== undefined) params.append('page', String(page));
      if (size !== undefined) params.append('size', String(size));
      const query = params.toString();
      const response = await this.axios.get<Page<FieldHistoryEntry>>(
        `/control/collections/${collectionId}/records/${recordId}/history/${fieldName}${query ? `?${query}` : ''}`
      );
      return response.data;
    },

    getFieldHistoryAcrossRecords: async (
      collectionId: string,
      fieldName: string,
      page?: number,
      size?: number
    ): Promise<Page<FieldHistoryEntry>> => {
      const params = new URLSearchParams();
      if (page !== undefined) params.append('page', String(page));
      if (size !== undefined) params.append('size', String(size));
      const query = params.toString();
      const response = await this.axios.get<Page<FieldHistoryEntry>>(
        `/control/collections/${collectionId}/field-history/${fieldName}${query ? `?${query}` : ''}`
      );
      return response.data;
    },

    getUserHistory: async (
      userId: string,
      page?: number,
      size?: number
    ): Promise<Page<FieldHistoryEntry>> => {
      const params = new URLSearchParams();
      if (page !== undefined) params.append('page', String(page));
      if (size !== undefined) params.append('size', String(size));
      const query = params.toString();
      const response = await this.axios.get<Page<FieldHistoryEntry>>(
        `/control/users/${userId}/field-history${query ? `?${query}` : ''}`
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

  /**
   * Page layout operations.
   * CRUD routed via /api/page-layouts (JSON:API, worker).
   * Layout assignments via /api/layout-assignments.
   */
  readonly layouts = {
    list: async (collectionId: string): Promise<PageLayout[]> => {
      const response = await this.axios.get(
        `/api/page-layouts?filter[collectionId][eq]=${encodeURIComponent(collectionId)}`
      );
      return unwrapJsonApiList<PageLayout>(response.data);
    },

    get: async (id: string): Promise<PageLayout> => {
      const response = await this.axios.get(`/api/page-layouts/${id}`);
      return unwrapJsonApiResource<PageLayout>(response.data);
    },

    create: async (_tenantId: string, request: CreatePageLayoutRequest): Promise<PageLayout> => {
      const body = toJsonApiBody('page-layouts', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/page-layouts', body);
      return unwrapJsonApiResource<PageLayout>(response.data);
    },

    update: async (id: string, request: Partial<CreatePageLayoutRequest>): Promise<PageLayout> => {
      const body = toJsonApiBody('page-layouts', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/page-layouts/${id}`, body);
      return unwrapJsonApiResource<PageLayout>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/page-layouts/${id}`);
    },

    listAssignments: async (collectionId: string): Promise<LayoutAssignment[]> => {
      const response = await this.axios.get(
        `/api/layout-assignments?filter[collectionId][eq]=${encodeURIComponent(collectionId)}`
      );
      return unwrapJsonApiList<LayoutAssignment>(response.data);
    },

    assign: async (request: LayoutAssignmentRequest): Promise<LayoutAssignment> => {
      const body = toJsonApiBody(
        'layout-assignments',
        request as unknown as Record<string, unknown>
      );
      const response = await this.axios.post('/api/layout-assignments', body);
      return unwrapJsonApiResource<LayoutAssignment>(response.data);
    },

    // TODO: resolve requires business logic (layout resolution by collection + profile + record type).
    // Needs a dedicated controller or action handler.
    resolve: async (
      collectionId: string,
      profileId?: string,
      recordTypeId?: string
    ): Promise<PageLayout> => {
      const params = new URLSearchParams({ collectionId });
      if (profileId) params.set('profileId', profileId);
      if (recordTypeId) params.set('recordTypeId', recordTypeId);
      const response = await this.axios.get<PageLayout>(
        `/control/layouts/resolve?${params.toString()}`
      );
      return response.data;
    },
  };

  /**
   * List view operations.
   * Routed via /api/list-views (JSON:API, worker).
   */
  readonly listViews = {
    list: async (
      _tenantId: string,
      collectionId: string,
      _userId?: string
    ): Promise<ListView[]> => {
      const response = await this.axios.get(
        `/api/list-views?filter[collectionId][eq]=${encodeURIComponent(collectionId)}`
      );
      return unwrapJsonApiList<ListView>(response.data);
    },

    get: async (id: string): Promise<ListView> => {
      const response = await this.axios.get(`/api/list-views/${id}`);
      return unwrapJsonApiResource<ListView>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateListViewRequest
    ): Promise<ListView> => {
      const body = toJsonApiBody('list-views', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/list-views', body);
      return unwrapJsonApiResource<ListView>(response.data);
    },

    update: async (id: string, request: Partial<CreateListViewRequest>): Promise<ListView> => {
      const body = toJsonApiBody('list-views', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/list-views/${id}`, body);
      return unwrapJsonApiResource<ListView>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/list-views/${id}`);
    },
  };

  /**
   * Report operations.
   * CRUD routed via /api/reports (JSON:API, worker).
   * Folders via /api/report-folders.
   */
  readonly reports = {
    list: async (_tenantId?: string, _userId?: string): Promise<Report[]> => {
      const response = await this.axios.get('/api/reports');
      return unwrapJsonApiList<Report>(response.data);
    },

    get: async (id: string): Promise<Report> => {
      const response = await this.axios.get(`/api/reports/${id}`);
      return unwrapJsonApiResource<Report>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateReportRequest
    ): Promise<Report> => {
      const body = toJsonApiBody('reports', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/reports', body);
      return unwrapJsonApiResource<Report>(response.data);
    },

    update: async (id: string, request: Partial<CreateReportRequest>): Promise<Report> => {
      const body = toJsonApiBody('reports', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/reports/${id}`, body);
      return unwrapJsonApiResource<Report>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/reports/${id}`);
    },

    listFolders: async (_tenantId?: string): Promise<ReportFolder[]> => {
      const response = await this.axios.get('/api/report-folders');
      return unwrapJsonApiList<ReportFolder>(response.data);
    },

    createFolder: async (
      _tenantId: string,
      _userId: string,
      name: string,
      accessLevel?: string
    ): Promise<ReportFolder> => {
      const attrs: Record<string, unknown> = { name };
      if (accessLevel) attrs.accessLevel = accessLevel;
      const body = toJsonApiBody('report-folders', attrs);
      const response = await this.axios.post('/api/report-folders', body);
      return unwrapJsonApiResource<ReportFolder>(response.data);
    },

    deleteFolder: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/report-folders/${id}`);
    },
  };

  /**
   * Dashboard operations.
   * Routed via /api/dashboards (JSON:API, worker).
   */
  readonly dashboards = {
    list: async (_tenantId?: string, _userId?: string): Promise<UserDashboard[]> => {
      const response = await this.axios.get('/api/dashboards');
      return unwrapJsonApiList<UserDashboard>(response.data);
    },

    get: async (id: string): Promise<UserDashboard> => {
      const response = await this.axios.get(`/api/dashboards/${id}`);
      return unwrapJsonApiResource<UserDashboard>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateDashboardRequest
    ): Promise<UserDashboard> => {
      const body = toJsonApiBody('dashboards', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/dashboards', body);
      return unwrapJsonApiResource<UserDashboard>(response.data);
    },

    update: async (
      id: string,
      request: Partial<CreateDashboardRequest>
    ): Promise<UserDashboard> => {
      const body = toJsonApiBody('dashboards', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/dashboards/${id}`, body);
      return unwrapJsonApiResource<UserDashboard>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/dashboards/${id}`);
    },
  };

  /**
   * Data export operations
   */
  readonly dataExport = {
    exportCsv: async (request: ExportRequest): Promise<Blob> => {
      const response = await this.axios.post('/control/export/csv', request, {
        responseType: 'blob',
      });
      return response.data as Blob;
    },

    exportXlsx: async (request: ExportRequest): Promise<Blob> => {
      const response = await this.axios.post('/control/export/xlsx', request, {
        responseType: 'blob',
      });
      return response.data as Blob;
    },
  };

  /**
   * Email template operations.
   * CRUD routed via /api/email-templates (JSON:API, worker).
   * Logs via /api/email-logs (tenant-wide collection).
   */
  readonly emailTemplates = {
    list: async (_tenantId?: string): Promise<EmailTemplate[]> => {
      const response = await this.axios.get('/api/email-templates');
      return unwrapJsonApiList<EmailTemplate>(response.data);
    },

    get: async (id: string): Promise<EmailTemplate> => {
      const response = await this.axios.get(`/api/email-templates/${id}`);
      return unwrapJsonApiResource<EmailTemplate>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateEmailTemplateRequest
    ): Promise<EmailTemplate> => {
      const body = toJsonApiBody('email-templates', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/email-templates', body);
      return unwrapJsonApiResource<EmailTemplate>(response.data);
    },

    update: async (
      id: string,
      request: Partial<CreateEmailTemplateRequest>
    ): Promise<EmailTemplate> => {
      const body = toJsonApiBody(
        'email-templates',
        request as unknown as Record<string, unknown>,
        id
      );
      const response = await this.axios.patch(`/api/email-templates/${id}`, body);
      return unwrapJsonApiResource<EmailTemplate>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/email-templates/${id}`);
    },

    listLogs: async (_tenantId?: string, _status?: string): Promise<EmailLog[]> => {
      const response = await this.axios.get('/api/email-logs');
      return unwrapJsonApiList<EmailLog>(response.data);
    },
  };

  /**
   * Workflow rule operations.
   * CRUD routed via /api/workflow-rules (JSON:API, worker).
   * Logs via /api/workflow-execution-logs (tenant-wide collection).
   */
  readonly workflowRules = {
    list: async (_tenantId?: string): Promise<WorkflowRule[]> => {
      const response = await this.axios.get('/api/workflow-rules');
      return unwrapJsonApiList<WorkflowRule>(response.data);
    },

    get: async (id: string): Promise<WorkflowRule> => {
      const response = await this.axios.get(`/api/workflow-rules/${id}`);
      return unwrapJsonApiResource<WorkflowRule>(response.data);
    },

    create: async (
      _tenantId: string,
      request: CreateWorkflowRuleRequest
    ): Promise<WorkflowRule> => {
      const body = toJsonApiBody('workflow-rules', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/workflow-rules', body);
      return unwrapJsonApiResource<WorkflowRule>(response.data);
    },

    update: async (
      id: string,
      request: Partial<CreateWorkflowRuleRequest>
    ): Promise<WorkflowRule> => {
      const body = toJsonApiBody(
        'workflow-rules',
        request as unknown as Record<string, unknown>,
        id
      );
      const response = await this.axios.patch(`/api/workflow-rules/${id}`, body);
      return unwrapJsonApiResource<WorkflowRule>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/workflow-rules/${id}`);
    },

    listLogs: async (_tenantId?: string): Promise<WorkflowExecutionLog[]> => {
      const response = await this.axios.get('/api/workflow-execution-logs');
      return unwrapJsonApiList<WorkflowExecutionLog>(response.data);
    },
  };

  /**
   * Approval process operations.
   * Process CRUD routed via /api/approval-processes (JSON:API, worker).
   * Instance operations remain on /control/ (ApprovalInstanceController exists).
   */
  readonly approvals = {
    listProcesses: async (_tenantId?: string): Promise<ApprovalProcess[]> => {
      const response = await this.axios.get('/api/approval-processes');
      return unwrapJsonApiList<ApprovalProcess>(response.data);
    },

    getProcess: async (id: string): Promise<ApprovalProcess> => {
      const response = await this.axios.get(`/api/approval-processes/${id}`);
      return unwrapJsonApiResource<ApprovalProcess>(response.data);
    },

    createProcess: async (
      _tenantId: string,
      request: CreateApprovalProcessRequest
    ): Promise<ApprovalProcess> => {
      const body = toJsonApiBody(
        'approval-processes',
        request as unknown as Record<string, unknown>
      );
      const response = await this.axios.post('/api/approval-processes', body);
      return unwrapJsonApiResource<ApprovalProcess>(response.data);
    },

    updateProcess: async (
      id: string,
      request: Partial<CreateApprovalProcessRequest>
    ): Promise<ApprovalProcess> => {
      const body = toJsonApiBody(
        'approval-processes',
        request as unknown as Record<string, unknown>,
        id
      );
      const response = await this.axios.patch(`/api/approval-processes/${id}`, body);
      return unwrapJsonApiResource<ApprovalProcess>(response.data);
    },

    deleteProcess: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/approval-processes/${id}`);
    },

    listInstances: async (tenantId: string): Promise<ApprovalInstance[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<ApprovalInstance[]>(
        `/control/approvals/instances?${params.toString()}`
      );
      return response.data;
    },

    getPendingForUser: async (userId: string): Promise<ApprovalInstance[]> => {
      const params = new URLSearchParams({ userId });
      const response = await this.axios.get<ApprovalInstance[]>(
        `/control/approvals/instances/pending?${params.toString()}`
      );
      return response.data;
    },
  };

  /**
   * Flow engine operations.
   * CRUD routed via /api/flows (JSON:API, worker).
   * Executions via /api/flow-executions (tenant-wide collection).
   */
  readonly flows = {
    list: async (_tenantId?: string): Promise<FlowDefinition[]> => {
      const response = await this.axios.get('/api/flows');
      return unwrapJsonApiList<FlowDefinition>(response.data);
    },

    get: async (id: string): Promise<FlowDefinition> => {
      const response = await this.axios.get(`/api/flows/${id}`);
      return unwrapJsonApiResource<FlowDefinition>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateFlowRequest
    ): Promise<FlowDefinition> => {
      const body = toJsonApiBody('flows', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/flows', body);
      return unwrapJsonApiResource<FlowDefinition>(response.data);
    },

    update: async (id: string, request: Partial<CreateFlowRequest>): Promise<FlowDefinition> => {
      const body = toJsonApiBody('flows', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/flows/${id}`, body);
      return unwrapJsonApiResource<FlowDefinition>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/flows/${id}`);
    },

    listExecutions: async (_tenantId?: string): Promise<FlowExecution[]> => {
      const response = await this.axios.get('/api/flow-executions');
      return unwrapJsonApiList<FlowExecution>(response.data);
    },

    getExecution: async (executionId: string): Promise<FlowExecution> => {
      const response = await this.axios.get(`/api/flow-executions/${executionId}`);
      return unwrapJsonApiResource<FlowExecution>(response.data);
    },
  };

  /**
   * Scheduled job operations.
   * CRUD routed via /api/scheduled-jobs (JSON:API, worker).
   * Logs via sub-resource /api/scheduled-jobs/{id}/job-execution-logs.
   */
  readonly scheduledJobs = {
    list: async (_tenantId?: string): Promise<ScheduledJob[]> => {
      const response = await this.axios.get('/api/scheduled-jobs');
      return unwrapJsonApiList<ScheduledJob>(response.data);
    },

    get: async (id: string): Promise<ScheduledJob> => {
      const response = await this.axios.get(`/api/scheduled-jobs/${id}`);
      return unwrapJsonApiResource<ScheduledJob>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateScheduledJobRequest
    ): Promise<ScheduledJob> => {
      const body = toJsonApiBody('scheduled-jobs', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/scheduled-jobs', body);
      return unwrapJsonApiResource<ScheduledJob>(response.data);
    },

    update: async (
      id: string,
      request: Partial<CreateScheduledJobRequest>
    ): Promise<ScheduledJob> => {
      const body = toJsonApiBody(
        'scheduled-jobs',
        request as unknown as Record<string, unknown>,
        id
      );
      const response = await this.axios.patch(`/api/scheduled-jobs/${id}`, body);
      return unwrapJsonApiResource<ScheduledJob>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/scheduled-jobs/${id}`);
    },

    listLogs: async (id: string): Promise<JobExecutionLog[]> => {
      const response = await this.axios.get(`/api/scheduled-jobs/${id}/job-execution-logs`);
      return unwrapJsonApiList<JobExecutionLog>(response.data);
    },
  };

  /**
   * Script operations.
   * CRUD routed via /api/scripts (JSON:API, worker).
   * Tenant-wide logs via /api/script-execution-logs.
   * Per-script logs via sub-resource /api/scripts/{id}/script-execution-logs.
   */
  readonly scripts = {
    list: async (_tenantId?: string): Promise<Script[]> => {
      const response = await this.axios.get('/api/scripts');
      return unwrapJsonApiList<Script>(response.data);
    },

    get: async (id: string): Promise<Script> => {
      const response = await this.axios.get(`/api/scripts/${id}`);
      return unwrapJsonApiResource<Script>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateScriptRequest
    ): Promise<Script> => {
      const body = toJsonApiBody('scripts', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/scripts', body);
      return unwrapJsonApiResource<Script>(response.data);
    },

    update: async (id: string, request: Partial<CreateScriptRequest>): Promise<Script> => {
      const body = toJsonApiBody('scripts', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/scripts/${id}`, body);
      return unwrapJsonApiResource<Script>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/scripts/${id}`);
    },

    listLogs: async (_tenantId?: string): Promise<ScriptExecutionLog[]> => {
      const response = await this.axios.get('/api/script-execution-logs');
      return unwrapJsonApiList<ScriptExecutionLog>(response.data);
    },

    listLogsByScript: async (id: string): Promise<ScriptExecutionLog[]> => {
      const response = await this.axios.get(`/api/scripts/${id}/script-execution-logs`);
      return unwrapJsonApiList<ScriptExecutionLog>(response.data);
    },
  };

  /**
   * Webhook operations.
   * CRUD routed via /api/webhooks (JSON:API, worker).
   * Deliveries via sub-resource /api/webhooks/{id}/webhook-deliveries.
   */
  readonly webhooks = {
    list: async (_tenantId?: string): Promise<Webhook[]> => {
      const response = await this.axios.get('/api/webhooks');
      return unwrapJsonApiList<Webhook>(response.data);
    },

    get: async (id: string): Promise<Webhook> => {
      const response = await this.axios.get(`/api/webhooks/${id}`);
      return unwrapJsonApiResource<Webhook>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateWebhookRequest
    ): Promise<Webhook> => {
      const body = toJsonApiBody('webhooks', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/webhooks', body);
      return unwrapJsonApiResource<Webhook>(response.data);
    },

    update: async (id: string, request: Partial<CreateWebhookRequest>): Promise<Webhook> => {
      const body = toJsonApiBody('webhooks', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/webhooks/${id}`, body);
      return unwrapJsonApiResource<Webhook>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/webhooks/${id}`);
    },

    listDeliveries: async (id: string): Promise<WebhookDelivery[]> => {
      const response = await this.axios.get(`/api/webhooks/${id}/webhook-deliveries`);
      return unwrapJsonApiList<WebhookDelivery>(response.data);
    },
  };

  /**
   * Connected app operations.
   * CRUD routed via /api/connected-apps (JSON:API, worker).
   * Rotate secret via action: POST /control/connected-apps/{id}/actions/rotate-secret.
   * Tokens via sub-resource /api/connected-apps/{id}/connected-app-tokens.
   */
  readonly connectedApps = {
    list: async (_tenantId?: string): Promise<ConnectedApp[]> => {
      const response = await this.axios.get('/api/connected-apps');
      return unwrapJsonApiList<ConnectedApp>(response.data);
    },

    get: async (id: string): Promise<ConnectedApp> => {
      const response = await this.axios.get(`/api/connected-apps/${id}`);
      return unwrapJsonApiResource<ConnectedApp>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateConnectedAppRequest
    ): Promise<ConnectedAppCreatedResponse> => {
      const body = toJsonApiBody('connected-apps', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/connected-apps', body);
      return unwrapJsonApiResource<ConnectedAppCreatedResponse>(response.data);
    },

    update: async (
      id: string,
      request: Partial<CreateConnectedAppRequest>
    ): Promise<ConnectedApp> => {
      const body = toJsonApiBody(
        'connected-apps',
        request as unknown as Record<string, unknown>,
        id
      );
      const response = await this.axios.patch(`/api/connected-apps/${id}`, body);
      return unwrapJsonApiResource<ConnectedApp>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/connected-apps/${id}`);
    },

    rotateSecret: async (id: string): Promise<ConnectedAppCreatedResponse> => {
      const response = await this.axios.post<ConnectedAppCreatedResponse>(
        `/control/connected-apps/${id}/actions/rotate-secret`
      );
      return response.data;
    },

    listTokens: async (id: string): Promise<ConnectedAppToken[]> => {
      const response = await this.axios.get(`/api/connected-apps/${id}/connected-app-tokens`);
      return unwrapJsonApiList<ConnectedAppToken>(response.data);
    },
  };

  /**
   * Bulk job operations.
   * CRUD routed via /api/bulk-jobs (JSON:API, worker).
   * Abort via action: POST /control/bulk-jobs/{id}/actions/abort.
   * Results via sub-resource /api/bulk-jobs/{id}/bulk-job-results.
   */
  readonly bulkJobs = {
    list: async (_tenantId?: string): Promise<BulkJob[]> => {
      const response = await this.axios.get('/api/bulk-jobs');
      return unwrapJsonApiList<BulkJob>(response.data);
    },

    get: async (id: string): Promise<BulkJob> => {
      const response = await this.axios.get(`/api/bulk-jobs/${id}`);
      return unwrapJsonApiResource<BulkJob>(response.data);
    },

    create: async (
      _tenantId: string,
      _userId: string,
      request: CreateBulkJobRequest
    ): Promise<BulkJob> => {
      const body = toJsonApiBody('bulk-jobs', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/bulk-jobs', body);
      return unwrapJsonApiResource<BulkJob>(response.data);
    },

    abort: async (id: string): Promise<BulkJob> => {
      const response = await this.axios.post<BulkJob>(`/control/bulk-jobs/${id}/actions/abort`);
      return response.data;
    },

    getResults: async (id: string): Promise<BulkJobResult[]> => {
      const response = await this.axios.get(`/api/bulk-jobs/${id}/bulk-job-results`);
      return unwrapJsonApiList<BulkJobResult>(response.data);
    },

    getErrors: async (id: string): Promise<BulkJobResult[]> => {
      const response = await this.axios.get(
        `/api/bulk-jobs/${id}/bulk-job-results?filter[status][eq]=ERROR`
      );
      return unwrapJsonApiList<BulkJobResult>(response.data);
    },
  };

  /**
   * Composite API operations
   */
  readonly composite = {
    execute: async (tenantId: string, request: CompositeRequest): Promise<CompositeResponse> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.post<CompositeResponse>(
        `/control/composite?${params.toString()}`,
        request
      );
      return response.data;
    },
  };
}
