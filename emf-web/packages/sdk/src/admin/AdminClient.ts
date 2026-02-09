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
   * Picklist management operations
   */
  readonly picklists = {
    listGlobal: async (tenantId = 'default'): Promise<GlobalPicklist[]> => {
      const params = new URLSearchParams();
      params.append('tenantId', tenantId);
      const response = await this.axios.get<GlobalPicklist[]>(
        `/control/picklists/global?${params.toString()}`
      );
      return response.data;
    },

    createGlobal: async (
      request: CreateGlobalPicklistRequest,
      tenantId = 'default'
    ): Promise<GlobalPicklist> => {
      const params = new URLSearchParams();
      params.append('tenantId', tenantId);
      const response = await this.axios.post<GlobalPicklist>(
        `/control/picklists/global?${params.toString()}`,
        request
      );
      return response.data;
    },

    getGlobal: async (id: string): Promise<GlobalPicklist> => {
      const response = await this.axios.get<GlobalPicklist>(`/control/picklists/global/${id}`);
      return response.data;
    },

    updateGlobal: async (
      id: string,
      request: Partial<CreateGlobalPicklistRequest>
    ): Promise<GlobalPicklist> => {
      const response = await this.axios.put<GlobalPicklist>(
        `/control/picklists/global/${id}`,
        request
      );
      return response.data;
    },

    deleteGlobal: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/picklists/global/${id}`);
    },

    getGlobalValues: async (id: string): Promise<PicklistValue[]> => {
      const response = await this.axios.get<PicklistValue[]>(
        `/control/picklists/global/${id}/values`
      );
      return response.data;
    },

    setGlobalValues: async (
      id: string,
      values: PicklistValueRequest[]
    ): Promise<PicklistValue[]> => {
      const response = await this.axios.put<PicklistValue[]>(
        `/control/picklists/global/${id}/values`,
        values
      );
      return response.data;
    },

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
   * Page layout operations
   */
  readonly layouts = {
    list: async (collectionId: string): Promise<PageLayout[]> => {
      const params = new URLSearchParams({ collectionId });
      const response = await this.axios.get<PageLayout[]>(`/control/layouts?${params.toString()}`);
      return response.data;
    },

    get: async (id: string): Promise<PageLayout> => {
      const response = await this.axios.get<PageLayout>(`/control/layouts/${id}`);
      return response.data;
    },

    create: async (tenantId: string, request: CreatePageLayoutRequest): Promise<PageLayout> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.post<PageLayout>(
        `/control/layouts?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (id: string, request: Partial<CreatePageLayoutRequest>): Promise<PageLayout> => {
      const response = await this.axios.put<PageLayout>(`/control/layouts/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/layouts/${id}`);
    },

    listAssignments: async (collectionId: string): Promise<LayoutAssignment[]> => {
      const params = new URLSearchParams({ collectionId });
      const response = await this.axios.get<LayoutAssignment[]>(
        `/control/layouts/assignments?${params.toString()}`
      );
      return response.data;
    },

    assign: async (request: LayoutAssignmentRequest): Promise<LayoutAssignment> => {
      const response = await this.axios.post<LayoutAssignment>(
        '/control/layouts/assignments',
        request
      );
      return response.data;
    },

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
   * List view operations
   */
  readonly listViews = {
    list: async (tenantId: string, collectionId: string, userId?: string): Promise<ListView[]> => {
      const params = new URLSearchParams({ tenantId, collectionId });
      if (userId) params.set('userId', userId);
      const response = await this.axios.get<ListView[]>(`/control/listviews?${params.toString()}`);
      return response.data;
    },

    get: async (id: string): Promise<ListView> => {
      const response = await this.axios.get<ListView>(`/control/listviews/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateListViewRequest
    ): Promise<ListView> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<ListView>(
        `/control/listviews?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (id: string, request: Partial<CreateListViewRequest>): Promise<ListView> => {
      const response = await this.axios.put<ListView>(`/control/listviews/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/listviews/${id}`);
    },
  };

  /**
   * Report operations
   */
  readonly reports = {
    list: async (tenantId: string, userId?: string): Promise<Report[]> => {
      const params = new URLSearchParams({ tenantId });
      if (userId) params.set('userId', userId);
      const response = await this.axios.get<Report[]>(`/control/reports?${params.toString()}`);
      return response.data;
    },

    get: async (id: string): Promise<Report> => {
      const response = await this.axios.get<Report>(`/control/reports/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateReportRequest
    ): Promise<Report> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<Report>(
        `/control/reports?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (id: string, request: Partial<CreateReportRequest>): Promise<Report> => {
      const response = await this.axios.put<Report>(`/control/reports/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/reports/${id}`);
    },

    listFolders: async (tenantId: string): Promise<ReportFolder[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<ReportFolder[]>(
        `/control/reports/folders?${params.toString()}`
      );
      return response.data;
    },

    createFolder: async (
      tenantId: string,
      userId: string,
      name: string,
      accessLevel?: string
    ): Promise<ReportFolder> => {
      const params = new URLSearchParams({ tenantId, userId, name });
      if (accessLevel) params.set('accessLevel', accessLevel);
      const response = await this.axios.post<ReportFolder>(
        `/control/reports/folders?${params.toString()}`
      );
      return response.data;
    },

    deleteFolder: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/reports/folders/${id}`);
    },
  };

  /**
   * Dashboard operations
   */
  readonly dashboards = {
    list: async (tenantId: string, userId?: string): Promise<UserDashboard[]> => {
      const params = new URLSearchParams({ tenantId });
      if (userId) params.set('userId', userId);
      const response = await this.axios.get<UserDashboard[]>(
        `/control/dashboards?${params.toString()}`
      );
      return response.data;
    },

    get: async (id: string): Promise<UserDashboard> => {
      const response = await this.axios.get<UserDashboard>(`/control/dashboards/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateDashboardRequest
    ): Promise<UserDashboard> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<UserDashboard>(
        `/control/dashboards?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (
      id: string,
      request: Partial<CreateDashboardRequest>
    ): Promise<UserDashboard> => {
      const response = await this.axios.put<UserDashboard>(`/control/dashboards/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/dashboards/${id}`);
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
   * Email template operations
   */
  readonly emailTemplates = {
    list: async (tenantId: string): Promise<EmailTemplate[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<EmailTemplate[]>(
        `/control/email-templates?${params.toString()}`
      );
      return response.data;
    },

    get: async (id: string): Promise<EmailTemplate> => {
      const response = await this.axios.get<EmailTemplate>(`/control/email-templates/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateEmailTemplateRequest
    ): Promise<EmailTemplate> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<EmailTemplate>(
        `/control/email-templates?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (
      id: string,
      request: Partial<CreateEmailTemplateRequest>
    ): Promise<EmailTemplate> => {
      const response = await this.axios.put<EmailTemplate>(
        `/control/email-templates/${id}`,
        request
      );
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/email-templates/${id}`);
    },

    listLogs: async (tenantId: string, status?: string): Promise<EmailLog[]> => {
      const params = new URLSearchParams({ tenantId });
      if (status) params.set('status', status);
      const response = await this.axios.get<EmailLog[]>(
        `/control/email-templates/logs?${params.toString()}`
      );
      return response.data;
    },
  };

  /**
   * Workflow rule operations
   */
  readonly workflowRules = {
    list: async (tenantId: string): Promise<WorkflowRule[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<WorkflowRule[]>(
        `/control/workflow-rules?${params.toString()}`
      );
      return response.data;
    },

    get: async (id: string): Promise<WorkflowRule> => {
      const response = await this.axios.get<WorkflowRule>(`/control/workflow-rules/${id}`);
      return response.data;
    },

    create: async (tenantId: string, request: CreateWorkflowRuleRequest): Promise<WorkflowRule> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.post<WorkflowRule>(
        `/control/workflow-rules?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (
      id: string,
      request: Partial<CreateWorkflowRuleRequest>
    ): Promise<WorkflowRule> => {
      const response = await this.axios.put<WorkflowRule>(`/control/workflow-rules/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/workflow-rules/${id}`);
    },

    listLogs: async (tenantId: string): Promise<WorkflowExecutionLog[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<WorkflowExecutionLog[]>(
        `/control/workflow-rules/logs?${params.toString()}`
      );
      return response.data;
    },
  };

  /**
   * Approval process operations
   */
  readonly approvals = {
    listProcesses: async (tenantId: string): Promise<ApprovalProcess[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<ApprovalProcess[]>(
        `/control/approvals/processes?${params.toString()}`
      );
      return response.data;
    },

    getProcess: async (id: string): Promise<ApprovalProcess> => {
      const response = await this.axios.get<ApprovalProcess>(`/control/approvals/processes/${id}`);
      return response.data;
    },

    createProcess: async (
      tenantId: string,
      request: CreateApprovalProcessRequest
    ): Promise<ApprovalProcess> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.post<ApprovalProcess>(
        `/control/approvals/processes?${params.toString()}`,
        request
      );
      return response.data;
    },

    updateProcess: async (
      id: string,
      request: Partial<CreateApprovalProcessRequest>
    ): Promise<ApprovalProcess> => {
      const response = await this.axios.put<ApprovalProcess>(
        `/control/approvals/processes/${id}`,
        request
      );
      return response.data;
    },

    deleteProcess: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/approvals/processes/${id}`);
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
   * Flow engine operations
   */
  readonly flows = {
    list: async (tenantId: string): Promise<FlowDefinition[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<FlowDefinition[]>(
        `/control/flows?${params.toString()}`
      );
      return response.data;
    },

    get: async (id: string): Promise<FlowDefinition> => {
      const response = await this.axios.get<FlowDefinition>(`/control/flows/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateFlowRequest
    ): Promise<FlowDefinition> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<FlowDefinition>(
        `/control/flows?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (id: string, request: Partial<CreateFlowRequest>): Promise<FlowDefinition> => {
      const response = await this.axios.put<FlowDefinition>(`/control/flows/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/flows/${id}`);
    },

    listExecutions: async (tenantId: string): Promise<FlowExecution[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<FlowExecution[]>(
        `/control/flows/executions?${params.toString()}`
      );
      return response.data;
    },

    getExecution: async (executionId: string): Promise<FlowExecution> => {
      const response = await this.axios.get<FlowExecution>(
        `/control/flows/executions/${executionId}`
      );
      return response.data;
    },
  };

  /**
   * Scheduled job operations
   */
  readonly scheduledJobs = {
    list: async (tenantId: string): Promise<ScheduledJob[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<ScheduledJob[]>(
        `/control/scheduled-jobs?${params.toString()}`
      );
      return response.data;
    },

    get: async (id: string): Promise<ScheduledJob> => {
      const response = await this.axios.get<ScheduledJob>(`/control/scheduled-jobs/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateScheduledJobRequest
    ): Promise<ScheduledJob> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<ScheduledJob>(
        `/control/scheduled-jobs?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (
      id: string,
      request: Partial<CreateScheduledJobRequest>
    ): Promise<ScheduledJob> => {
      const response = await this.axios.put<ScheduledJob>(`/control/scheduled-jobs/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/scheduled-jobs/${id}`);
    },

    listLogs: async (id: string): Promise<JobExecutionLog[]> => {
      const response = await this.axios.get<JobExecutionLog[]>(
        `/control/scheduled-jobs/${id}/logs`
      );
      return response.data;
    },
  };

  /**
   * Script operations
   */
  readonly scripts = {
    list: async (tenantId: string): Promise<Script[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<Script[]>(`/control/scripts?${params.toString()}`);
      return response.data;
    },

    get: async (id: string): Promise<Script> => {
      const response = await this.axios.get<Script>(`/control/scripts/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateScriptRequest
    ): Promise<Script> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<Script>(
        `/control/scripts?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (id: string, request: Partial<CreateScriptRequest>): Promise<Script> => {
      const response = await this.axios.put<Script>(`/control/scripts/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/scripts/${id}`);
    },

    listLogs: async (tenantId: string): Promise<ScriptExecutionLog[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<ScriptExecutionLog[]>(
        `/control/scripts/logs?${params.toString()}`
      );
      return response.data;
    },

    listLogsByScript: async (id: string): Promise<ScriptExecutionLog[]> => {
      const response = await this.axios.get<ScriptExecutionLog[]>(`/control/scripts/${id}/logs`);
      return response.data;
    },
  };

  /**
   * Webhook operations
   */
  readonly webhooks = {
    list: async (tenantId: string): Promise<Webhook[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<Webhook[]>(`/control/webhooks?${params.toString()}`);
      return response.data;
    },

    get: async (id: string): Promise<Webhook> => {
      const response = await this.axios.get<Webhook>(`/control/webhooks/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateWebhookRequest
    ): Promise<Webhook> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<Webhook>(
        `/control/webhooks?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (id: string, request: Partial<CreateWebhookRequest>): Promise<Webhook> => {
      const response = await this.axios.put<Webhook>(`/control/webhooks/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/webhooks/${id}`);
    },

    listDeliveries: async (id: string): Promise<WebhookDelivery[]> => {
      const response = await this.axios.get<WebhookDelivery[]>(
        `/control/webhooks/${id}/deliveries`
      );
      return response.data;
    },
  };

  /**
   * Connected app operations
   */
  readonly connectedApps = {
    list: async (tenantId: string): Promise<ConnectedApp[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<ConnectedApp[]>(
        `/control/connected-apps?${params.toString()}`
      );
      return response.data;
    },

    get: async (id: string): Promise<ConnectedApp> => {
      const response = await this.axios.get<ConnectedApp>(`/control/connected-apps/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateConnectedAppRequest
    ): Promise<ConnectedAppCreatedResponse> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<ConnectedAppCreatedResponse>(
        `/control/connected-apps?${params.toString()}`,
        request
      );
      return response.data;
    },

    update: async (
      id: string,
      request: Partial<CreateConnectedAppRequest>
    ): Promise<ConnectedApp> => {
      const response = await this.axios.put<ConnectedApp>(`/control/connected-apps/${id}`, request);
      return response.data;
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/control/connected-apps/${id}`);
    },

    rotateSecret: async (id: string): Promise<ConnectedAppCreatedResponse> => {
      const response = await this.axios.post<ConnectedAppCreatedResponse>(
        `/control/connected-apps/${id}/rotate-secret`
      );
      return response.data;
    },

    listTokens: async (id: string): Promise<ConnectedAppToken[]> => {
      const response = await this.axios.get<ConnectedAppToken[]>(
        `/control/connected-apps/${id}/tokens`
      );
      return response.data;
    },
  };

  /**
   * Bulk job operations
   */
  readonly bulkJobs = {
    list: async (tenantId: string): Promise<BulkJob[]> => {
      const params = new URLSearchParams({ tenantId });
      const response = await this.axios.get<BulkJob[]>(`/control/bulk-jobs?${params.toString()}`);
      return response.data;
    },

    get: async (id: string): Promise<BulkJob> => {
      const response = await this.axios.get<BulkJob>(`/control/bulk-jobs/${id}`);
      return response.data;
    },

    create: async (
      tenantId: string,
      userId: string,
      request: CreateBulkJobRequest
    ): Promise<BulkJob> => {
      const params = new URLSearchParams({ tenantId, userId });
      const response = await this.axios.post<BulkJob>(
        `/control/bulk-jobs?${params.toString()}`,
        request
      );
      return response.data;
    },

    abort: async (id: string): Promise<BulkJob> => {
      const response = await this.axios.post<BulkJob>(`/control/bulk-jobs/${id}/abort`);
      return response.data;
    },

    getResults: async (id: string): Promise<BulkJobResult[]> => {
      const response = await this.axios.get<BulkJobResult[]>(`/control/bulk-jobs/${id}/results`);
      return response.data;
    },

    getErrors: async (id: string): Promise<BulkJobResult[]> => {
      const response = await this.axios.get<BulkJobResult[]>(`/control/bulk-jobs/${id}/errors`);
      return response.data;
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
