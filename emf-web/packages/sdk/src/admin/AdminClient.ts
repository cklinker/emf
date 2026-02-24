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
import {
  toJsonApiBody,
  unwrapJsonApiResource,
  unwrapJsonApiList,
  extractMetadata,
  buildJsonApiParams,
} from './jsonapi-helpers';

/**
 * Default theme and branding constants for bootstrap.
 * These provide fallback values when the tenant has no custom theme.
 */
const DEFAULT_THEME = {
  primaryColor: '#1976d2',
  secondaryColor: '#dc004e',
  fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
  borderRadius: '8px',
};

const DEFAULT_BRANDING = {
  logoUrl: '',
  applicationName: 'EMF',
  faviconUrl: '',
};

/**
 * Convert JSON:API metadata into a Spring-style Page<T> response.
 */
function toPage<T>(items: T[], metadata: unknown): Page<T> {
  const meta = metadata as Record<string, number> | undefined;
  return {
    content: items,
    totalElements: meta?.totalCount ?? items.length,
    totalPages: meta?.totalPages ?? 1,
    size: meta?.pageSize ?? items.length,
    number: meta?.currentPage ?? 0,
  };
}

/**
 * Admin client for platform operations.
 *
 * All endpoints route through the worker's DynamicCollectionRouter
 * via `/api/{collection}` (JSON:API format). The control plane has
 * been removed; all system collections are served by the worker.
 */
export class AdminClient {
  constructor(private readonly axios: AxiosInstance) {}

  // ---------------------------------------------------------------------------
  // Collections
  // ---------------------------------------------------------------------------

  readonly collections = {
    list: async (): Promise<CollectionDefinition[]> => {
      const response = await this.axios.get('/api/collections');
      return unwrapJsonApiList<CollectionDefinition>(response.data);
    },

    get: async (id: string): Promise<CollectionDefinition> => {
      const response = await this.axios.get(`/api/collections/${id}`);
      return unwrapJsonApiResource<CollectionDefinition>(response.data);
    },

    create: async (definition: CollectionDefinition): Promise<CollectionDefinition> => {
      const body = toJsonApiBody('collections', definition as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/collections', body);
      return unwrapJsonApiResource<CollectionDefinition>(response.data);
    },

    update: async (id: string, definition: CollectionDefinition): Promise<CollectionDefinition> => {
      const body = toJsonApiBody(
        'collections',
        definition as unknown as Record<string, unknown>,
        id
      );
      const response = await this.axios.patch(`/api/collections/${id}`, body);
      return unwrapJsonApiResource<CollectionDefinition>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/collections/${id}`);
    },
  };

  // ---------------------------------------------------------------------------
  // Fields
  // ---------------------------------------------------------------------------

  readonly fields = {
    add: async (collectionId: string, field: FieldDefinition): Promise<FieldDefinition> => {
      const body = toJsonApiBody('fields', {
        ...(field as unknown as Record<string, unknown>),
        collectionId,
      });
      const response = await this.axios.post('/api/fields', body);
      return unwrapJsonApiResource<FieldDefinition>(response.data);
    },

    update: async (
      _collectionId: string,
      fieldId: string,
      field: FieldDefinition
    ): Promise<FieldDefinition> => {
      const body = toJsonApiBody('fields', field as unknown as Record<string, unknown>, fieldId);
      const response = await this.axios.patch(`/api/fields/${fieldId}`, body);
      return unwrapJsonApiResource<FieldDefinition>(response.data);
    },

    delete: async (_collectionId: string, fieldId: string): Promise<void> => {
      await this.axios.delete(`/api/fields/${fieldId}`);
    },
  };

  // ---------------------------------------------------------------------------
  // Authorization (roles & policies)
  // ---------------------------------------------------------------------------

  readonly authz = {
    listRoles: async (): Promise<Role[]> => {
      const response = await this.axios.get('/api/roles');
      return unwrapJsonApiList<Role>(response.data);
    },

    createRole: async (role: Role): Promise<Role> => {
      const body = toJsonApiBody('roles', role as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/roles', body);
      return unwrapJsonApiResource<Role>(response.data);
    },

    updateRole: async (id: string, role: Role): Promise<Role> => {
      const body = toJsonApiBody('roles', role as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/roles/${id}`, body);
      return unwrapJsonApiResource<Role>(response.data);
    },

    deleteRole: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/roles/${id}`);
    },

    listPolicies: async (): Promise<Policy[]> => {
      const response = await this.axios.get('/api/policies');
      return unwrapJsonApiList<Policy>(response.data);
    },

    createPolicy: async (policy: Policy): Promise<Policy> => {
      const body = toJsonApiBody('policies', policy as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/policies', body);
      return unwrapJsonApiResource<Policy>(response.data);
    },

    updatePolicy: async (id: string, policy: Policy): Promise<Policy> => {
      const body = toJsonApiBody('policies', policy as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/policies/${id}`, body);
      return unwrapJsonApiResource<Policy>(response.data);
    },

    deletePolicy: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/policies/${id}`);
    },
  };

  // ---------------------------------------------------------------------------
  // OIDC providers (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // UI configuration (bootstrap, pages, menus)
  // ---------------------------------------------------------------------------

  readonly ui = {
    /**
     * Compose bootstrap configuration from individual JSON:API endpoints.
     * Replaces the old single /control/ui-bootstrap endpoint.
     */
    getBootstrap: async (): Promise<UIConfig> => {
      const [pagesRes, menusRes, providersRes] = await Promise.all([
        this.axios.get('/api/ui-pages'),
        this.axios.get('/api/ui-menus'),
        this.axios.get('/api/oidc-providers'),
      ]);
      return {
        pages: unwrapJsonApiList(pagesRes.data),
        menus: unwrapJsonApiList(menusRes.data),
        oidcProviders: unwrapJsonApiList(providersRes.data),
        theme: DEFAULT_THEME,
        branding: DEFAULT_BRANDING,
      } as UIConfig;
    },

    listPages: async (): Promise<UIPage[]> => {
      const response = await this.axios.get('/api/ui-pages');
      return unwrapJsonApiList<UIPage>(response.data);
    },

    createPage: async (page: UIPage): Promise<UIPage> => {
      const body = toJsonApiBody('ui-pages', page as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/ui-pages', body);
      return unwrapJsonApiResource<UIPage>(response.data);
    },

    updatePage: async (id: string, page: UIPage): Promise<UIPage> => {
      const body = toJsonApiBody('ui-pages', page as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/ui-pages/${id}`, body);
      return unwrapJsonApiResource<UIPage>(response.data);
    },

    listMenus: async (): Promise<UIMenu[]> => {
      const response = await this.axios.get('/api/ui-menus');
      return unwrapJsonApiList<UIMenu>(response.data);
    },

    updateMenu: async (id: string, menu: UIMenu): Promise<UIMenu> => {
      const body = toJsonApiBody('ui-menus', menu as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/ui-menus/${id}`, body);
      return unwrapJsonApiResource<UIMenu>(response.data);
    },
  };

  // ---------------------------------------------------------------------------
  // Packages — graceful degradation (needs server-side logic)
  // ---------------------------------------------------------------------------

  readonly packages = {
    export: (_options: ExportOptions): Promise<PackageData> => {
      throw new Error('Package export is temporarily unavailable');
    },

    import: (_packageData: PackageData): Promise<ImportResult> => {
      throw new Error('Package import is temporarily unavailable');
    },
  };

  // ---------------------------------------------------------------------------
  // Tenants
  // ---------------------------------------------------------------------------

  readonly tenants = {
    list: async (page = 0, size = 20): Promise<Page<Tenant>> => {
      const qs = buildJsonApiParams({ page, size });
      const response = await this.axios.get(`/api/tenants${qs}`);
      const items = unwrapJsonApiList<Tenant>(response.data);
      const meta = extractMetadata(response.data);
      return toPage(items, meta);
    },

    get: async (id: string): Promise<Tenant> => {
      const response = await this.axios.get(`/api/tenants/${id}`);
      return unwrapJsonApiResource<Tenant>(response.data);
    },

    create: async (request: CreateTenantRequest): Promise<Tenant> => {
      const body = toJsonApiBody('tenants', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/tenants', body);
      return unwrapJsonApiResource<Tenant>(response.data);
    },

    update: async (id: string, request: UpdateTenantRequest): Promise<Tenant> => {
      const body = toJsonApiBody('tenants', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/tenants/${id}`, body);
      return unwrapJsonApiResource<Tenant>(response.data);
    },

    suspend: async (id: string): Promise<void> => {
      const body = toJsonApiBody('tenants', { status: 'SUSPENDED' }, id);
      await this.axios.patch(`/api/tenants/${id}`, body);
    },

    activate: async (id: string): Promise<void> => {
      const body = toJsonApiBody('tenants', { status: 'ACTIVE' }, id);
      await this.axios.patch(`/api/tenants/${id}`, body);
    },

    getLimits: async (id: string): Promise<GovernorLimits> => {
      // Governor limits are per-tenant; fetch from governor-limits collection
      const response = await this.axios.get(
        `/api/governor-limits?filter[tenantId][eq]=${encodeURIComponent(id)}`
      );
      const items = unwrapJsonApiList<GovernorLimits>(response.data);
      return items[0] ?? ({} as GovernorLimits);
    },
  };

  // ---------------------------------------------------------------------------
  // Users
  // ---------------------------------------------------------------------------

  readonly users = {
    list: async (
      filter?: string,
      status?: string,
      page = 0,
      size = 20
    ): Promise<Page<PlatformUser>> => {
      const params: Record<string, string> = {};
      if (filter) params['filter[search][contains]'] = filter;
      if (status) params['filter[status][eq]'] = status;
      const qs = buildJsonApiParams({ page, size, filters: params });
      const response = await this.axios.get(`/api/users${qs}`);
      const items = unwrapJsonApiList<PlatformUser>(response.data);
      const meta = extractMetadata(response.data);
      return toPage(items, meta);
    },

    get: async (id: string): Promise<PlatformUser> => {
      const response = await this.axios.get(`/api/users/${id}`);
      return unwrapJsonApiResource<PlatformUser>(response.data);
    },

    create: async (request: CreatePlatformUserRequest): Promise<PlatformUser> => {
      const body = toJsonApiBody('users', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/users', body);
      return unwrapJsonApiResource<PlatformUser>(response.data);
    },

    update: async (id: string, request: UpdatePlatformUserRequest): Promise<PlatformUser> => {
      const body = toJsonApiBody('users', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/users/${id}`, body);
      return unwrapJsonApiResource<PlatformUser>(response.data);
    },

    deactivate: async (id: string): Promise<void> => {
      const body = toJsonApiBody('users', { active: false }, id);
      await this.axios.patch(`/api/users/${id}`, body);
    },

    activate: async (id: string): Promise<void> => {
      const body = toJsonApiBody('users', { active: true }, id);
      await this.axios.patch(`/api/users/${id}`, body);
    },

    getLoginHistory: async (id: string, page = 0, size = 20): Promise<Page<LoginHistoryEntry>> => {
      const qs = buildJsonApiParams({
        page,
        size,
        filters: { 'filter[userId][eq]': id },
      });
      const response = await this.axios.get(`/api/login-history${qs}`);
      const items = unwrapJsonApiList<LoginHistoryEntry>(response.data);
      const meta = extractMetadata(response.data);
      return toPage(items, meta);
    },
  };

  // ---------------------------------------------------------------------------
  // Picklists (global already on /api/, field-level migrated)
  // ---------------------------------------------------------------------------

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

    getFieldValues: async (fieldId: string): Promise<PicklistValue[]> => {
      const response = await this.axios.get(
        `/api/picklist-values?filter[fieldId][eq]=${encodeURIComponent(fieldId)}&filter[source][eq]=FIELD`
      );
      return unwrapJsonApiList<PicklistValue>(response.data);
    },

    setFieldValues: async (
      fieldId: string,
      values: PicklistValueRequest[]
    ): Promise<PicklistValue[]> => {
      // Delete existing field values, then create new ones
      const existingResponse = await this.axios.get(
        `/api/picklist-values?filter[fieldId][eq]=${encodeURIComponent(fieldId)}&filter[source][eq]=FIELD`
      );
      const existing = unwrapJsonApiList<PicklistValue>(existingResponse.data);
      await Promise.all(existing.map((v) => this.axios.delete(`/api/picklist-values/${v.id}`)));
      const created = await Promise.all(
        values.map((v) => {
          const body = toJsonApiBody('picklist-values', {
            ...(v as unknown as Record<string, unknown>),
            fieldId,
            source: 'FIELD',
          });
          return this.axios.post('/api/picklist-values', body);
        })
      );
      return created.map((r) => unwrapJsonApiResource<PicklistValue>(r.data));
    },

    getDependencies: async (fieldId: string): Promise<PicklistDependency[]> => {
      const response = await this.axios.get(
        `/api/picklist-dependencies?filter[controllingFieldId][eq]=${encodeURIComponent(fieldId)}`
      );
      return unwrapJsonApiList<PicklistDependency>(response.data);
    },

    setDependency: async (request: SetDependencyRequest): Promise<PicklistDependency> => {
      const body = toJsonApiBody(
        'picklist-dependencies',
        request as unknown as Record<string, unknown>
      );
      const response = await this.axios.post('/api/picklist-dependencies', body);
      return unwrapJsonApiResource<PicklistDependency>(response.data);
    },

    removeDependency: async (
      controllingFieldId: string,
      dependentFieldId: string
    ): Promise<void> => {
      // Find the dependency record first, then delete
      const response = await this.axios.get(
        `/api/picklist-dependencies?filter[controllingFieldId][eq]=${encodeURIComponent(controllingFieldId)}&filter[dependentFieldId][eq]=${encodeURIComponent(dependentFieldId)}`
      );
      const deps = unwrapJsonApiList<PicklistDependency>(response.data);
      if (deps.length > 0) {
        await this.axios.delete(`/api/picklist-dependencies/${deps[0].id}`);
      }
    },
  };

  // ---------------------------------------------------------------------------
  // Relationships
  // ---------------------------------------------------------------------------

  readonly relationships = {
    getForCollection: async (collectionId: string): Promise<CollectionRelationships> => {
      // Fetch reference-type fields for the collection to derive relationships
      const response = await this.axios.get(
        `/api/fields?filter[collectionId][eq]=${encodeURIComponent(collectionId)}&filter[type][eq]=REFERENCE`
      );
      const fields = unwrapJsonApiList<FieldDefinition>(response.data);
      return { fields } as unknown as CollectionRelationships;
    },
  };

  // ---------------------------------------------------------------------------
  // Validation rules
  // ---------------------------------------------------------------------------

  readonly validationRules = {
    list: async (collectionId: string): Promise<CollectionValidationRule[]> => {
      const response = await this.axios.get(
        `/api/validation-rules?filter[collectionId][eq]=${encodeURIComponent(collectionId)}`
      );
      return unwrapJsonApiList<CollectionValidationRule>(response.data);
    },

    create: async (
      collectionId: string,
      request: CreateCollectionValidationRuleRequest
    ): Promise<CollectionValidationRule> => {
      const body = toJsonApiBody('validation-rules', {
        ...(request as unknown as Record<string, unknown>),
        collectionId,
      });
      const response = await this.axios.post('/api/validation-rules', body);
      return unwrapJsonApiResource<CollectionValidationRule>(response.data);
    },

    get: async (_collectionId: string, ruleId: string): Promise<CollectionValidationRule> => {
      const response = await this.axios.get(`/api/validation-rules/${ruleId}`);
      return unwrapJsonApiResource<CollectionValidationRule>(response.data);
    },

    update: async (
      _collectionId: string,
      ruleId: string,
      request: Partial<CreateCollectionValidationRuleRequest> & { active?: boolean }
    ): Promise<CollectionValidationRule> => {
      const body = toJsonApiBody(
        'validation-rules',
        request as unknown as Record<string, unknown>,
        ruleId
      );
      const response = await this.axios.patch(`/api/validation-rules/${ruleId}`, body);
      return unwrapJsonApiResource<CollectionValidationRule>(response.data);
    },

    delete: async (_collectionId: string, ruleId: string): Promise<void> => {
      await this.axios.delete(`/api/validation-rules/${ruleId}`);
    },

    activate: async (_collectionId: string, ruleId: string): Promise<void> => {
      const body = toJsonApiBody('validation-rules', { active: true }, ruleId);
      await this.axios.patch(`/api/validation-rules/${ruleId}`, body);
    },

    deactivate: async (_collectionId: string, ruleId: string): Promise<void> => {
      const body = toJsonApiBody('validation-rules', { active: false }, ruleId);
      await this.axios.patch(`/api/validation-rules/${ruleId}`, body);
    },

    test: (
      _collectionId: string,
      _testRecord: Record<string, unknown>
    ): Promise<CollectionValidationError[]> => {
      // Validation test requires server-side logic — temporarily unavailable
      return Promise.resolve([]);
    },
  };

  // ---------------------------------------------------------------------------
  // Record types
  // ---------------------------------------------------------------------------

  readonly recordTypes = {
    list: async (collectionId: string): Promise<RecordType[]> => {
      const response = await this.axios.get(
        `/api/record-types?filter[collectionId][eq]=${encodeURIComponent(collectionId)}`
      );
      return unwrapJsonApiList<RecordType>(response.data);
    },

    create: async (collectionId: string, request: CreateRecordTypeRequest): Promise<RecordType> => {
      const body = toJsonApiBody('record-types', {
        ...(request as unknown as Record<string, unknown>),
        collectionId,
      });
      const response = await this.axios.post('/api/record-types', body);
      return unwrapJsonApiResource<RecordType>(response.data);
    },

    get: async (_collectionId: string, recordTypeId: string): Promise<RecordType> => {
      const response = await this.axios.get(`/api/record-types/${recordTypeId}`);
      return unwrapJsonApiResource<RecordType>(response.data);
    },

    update: async (
      _collectionId: string,
      recordTypeId: string,
      request: Partial<CreateRecordTypeRequest> & { active?: boolean }
    ): Promise<RecordType> => {
      const body = toJsonApiBody(
        'record-types',
        request as unknown as Record<string, unknown>,
        recordTypeId
      );
      const response = await this.axios.patch(`/api/record-types/${recordTypeId}`, body);
      return unwrapJsonApiResource<RecordType>(response.data);
    },

    delete: async (_collectionId: string, recordTypeId: string): Promise<void> => {
      await this.axios.delete(`/api/record-types/${recordTypeId}`);
    },

    getPicklistOverrides: async (
      _collectionId: string,
      recordTypeId: string
    ): Promise<RecordTypePicklistOverride[]> => {
      const response = await this.axios.get(
        `/api/record-type-picklists?filter[recordTypeId][eq]=${encodeURIComponent(recordTypeId)}`
      );
      return unwrapJsonApiList<RecordTypePicklistOverride>(response.data);
    },

    setPicklistOverride: async (
      _collectionId: string,
      recordTypeId: string,
      fieldId: string,
      request: SetPicklistOverrideRequest
    ): Promise<RecordTypePicklistOverride> => {
      const body = toJsonApiBody('record-type-picklists', {
        ...(request as unknown as Record<string, unknown>),
        recordTypeId,
        fieldId,
      });
      const response = await this.axios.post('/api/record-type-picklists', body);
      return unwrapJsonApiResource<RecordTypePicklistOverride>(response.data);
    },

    removePicklistOverride: async (
      _collectionId: string,
      _recordTypeId: string,
      picklistOverrideId: string
    ): Promise<void> => {
      await this.axios.delete(`/api/record-type-picklists/${picklistOverrideId}`);
    },
  };

  // ---------------------------------------------------------------------------
  // Field history
  // ---------------------------------------------------------------------------

  readonly fieldHistory = {
    getRecordHistory: async (
      collectionId: string,
      recordId: string,
      page?: number,
      size?: number
    ): Promise<Page<FieldHistoryEntry>> => {
      const qs = buildJsonApiParams({
        page,
        size,
        filters: {
          'filter[collectionId][eq]': collectionId,
          'filter[recordId][eq]': recordId,
        },
      });
      const response = await this.axios.get(`/api/field-history${qs}`);
      const items = unwrapJsonApiList<FieldHistoryEntry>(response.data);
      const meta = extractMetadata(response.data);
      return toPage(items, meta);
    },

    getFieldHistory: async (
      collectionId: string,
      recordId: string,
      fieldName: string,
      page?: number,
      size?: number
    ): Promise<Page<FieldHistoryEntry>> => {
      const qs = buildJsonApiParams({
        page,
        size,
        filters: {
          'filter[collectionId][eq]': collectionId,
          'filter[recordId][eq]': recordId,
          'filter[fieldName][eq]': fieldName,
        },
      });
      const response = await this.axios.get(`/api/field-history${qs}`);
      const items = unwrapJsonApiList<FieldHistoryEntry>(response.data);
      const meta = extractMetadata(response.data);
      return toPage(items, meta);
    },

    getFieldHistoryAcrossRecords: async (
      collectionId: string,
      fieldName: string,
      page?: number,
      size?: number
    ): Promise<Page<FieldHistoryEntry>> => {
      const qs = buildJsonApiParams({
        page,
        size,
        filters: {
          'filter[collectionId][eq]': collectionId,
          'filter[fieldName][eq]': fieldName,
        },
      });
      const response = await this.axios.get(`/api/field-history${qs}`);
      const items = unwrapJsonApiList<FieldHistoryEntry>(response.data);
      const meta = extractMetadata(response.data);
      return toPage(items, meta);
    },

    getUserHistory: async (
      userId: string,
      page?: number,
      size?: number
    ): Promise<Page<FieldHistoryEntry>> => {
      const qs = buildJsonApiParams({
        page,
        size,
        filters: { 'filter[userId][eq]': userId },
      });
      const response = await this.axios.get(`/api/field-history${qs}`);
      const items = unwrapJsonApiList<FieldHistoryEntry>(response.data);
      const meta = extractMetadata(response.data);
      return toPage(items, meta);
    },
  };

  // ---------------------------------------------------------------------------
  // Migrations
  // ---------------------------------------------------------------------------

  readonly migrations = {
    plan: (_collectionId: string, _targetSchema: CollectionDefinition): Promise<MigrationPlan> => {
      throw new Error('Migration planning is temporarily unavailable');
    },

    listRuns: async (): Promise<Migration[]> => {
      const response = await this.axios.get('/api/migration-runs');
      return unwrapJsonApiList<Migration>(response.data);
    },

    getRun: async (id: string): Promise<MigrationRun> => {
      const response = await this.axios.get(`/api/migration-runs/${id}`);
      return unwrapJsonApiResource<MigrationRun>(response.data);
    },
  };

  // ---------------------------------------------------------------------------
  // Profiles
  // ---------------------------------------------------------------------------

  readonly profiles = {
    list: async (): Promise<Profile[]> => {
      const response = await this.axios.get('/api/profiles');
      return unwrapJsonApiList<Profile>(response.data);
    },

    get: async (id: string): Promise<Profile> => {
      const response = await this.axios.get(`/api/profiles/${id}`);
      return unwrapJsonApiResource<Profile>(response.data);
    },

    create: async (request: CreateProfileRequest): Promise<Profile> => {
      const body = toJsonApiBody('profiles', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/profiles', body);
      return unwrapJsonApiResource<Profile>(response.data);
    },

    update: async (id: string, request: UpdateProfileRequest): Promise<Profile> => {
      const body = toJsonApiBody('profiles', request as unknown as Record<string, unknown>, id);
      const response = await this.axios.patch(`/api/profiles/${id}`, body);
      return unwrapJsonApiResource<Profile>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/profiles/${id}`);
    },

    setObjectPermissions: async (
      id: string,
      collectionId: string,
      perms: ObjectPermissionRequest
    ): Promise<void> => {
      const body = toJsonApiBody('profile-object-permissions', {
        ...(perms as unknown as Record<string, unknown>),
        profileId: id,
        collectionId,
      });
      await this.axios.post('/api/profile-object-permissions', body);
    },

    setFieldPermissions: async (id: string, perms: FieldPermissionRequest[]): Promise<void> => {
      await Promise.all(
        perms.map((p) => {
          const body = toJsonApiBody('profile-field-permissions', {
            ...(p as unknown as Record<string, unknown>),
            profileId: id,
          });
          return this.axios.post('/api/profile-field-permissions', body);
        })
      );
    },

    setSystemPermissions: async (id: string, perms: SystemPermissionRequest[]): Promise<void> => {
      await Promise.all(
        perms.map((p) => {
          const body = toJsonApiBody('profile-system-permissions', {
            ...(p as unknown as Record<string, unknown>),
            profileId: id,
          });
          return this.axios.post('/api/profile-system-permissions', body);
        })
      );
    },
  };

  // ---------------------------------------------------------------------------
  // Permission sets
  // ---------------------------------------------------------------------------

  readonly permissionSets = {
    list: async (): Promise<PermissionSet[]> => {
      const response = await this.axios.get('/api/permission-sets');
      return unwrapJsonApiList<PermissionSet>(response.data);
    },

    get: async (id: string): Promise<PermissionSet> => {
      const response = await this.axios.get(`/api/permission-sets/${id}`);
      return unwrapJsonApiResource<PermissionSet>(response.data);
    },

    create: async (request: CreatePermissionSetRequest): Promise<PermissionSet> => {
      const body = toJsonApiBody('permission-sets', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/permission-sets', body);
      return unwrapJsonApiResource<PermissionSet>(response.data);
    },

    update: async (id: string, request: UpdatePermissionSetRequest): Promise<PermissionSet> => {
      const body = toJsonApiBody(
        'permission-sets',
        request as unknown as Record<string, unknown>,
        id
      );
      const response = await this.axios.patch(`/api/permission-sets/${id}`, body);
      return unwrapJsonApiResource<PermissionSet>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/permission-sets/${id}`);
    },

    setObjectPermissions: async (
      id: string,
      collectionId: string,
      perms: ObjectPermissionRequest
    ): Promise<void> => {
      const body = toJsonApiBody('permset-object-permissions', {
        ...(perms as unknown as Record<string, unknown>),
        permissionSetId: id,
        collectionId,
      });
      await this.axios.post('/api/permset-object-permissions', body);
    },

    setFieldPermissions: async (id: string, perms: FieldPermissionRequest[]): Promise<void> => {
      await Promise.all(
        perms.map((p) => {
          const body = toJsonApiBody('permset-field-permissions', {
            ...(p as unknown as Record<string, unknown>),
            permissionSetId: id,
          });
          return this.axios.post('/api/permset-field-permissions', body);
        })
      );
    },

    setSystemPermissions: async (id: string, perms: SystemPermissionRequest[]): Promise<void> => {
      await Promise.all(
        perms.map((p) => {
          const body = toJsonApiBody('permset-system-permissions', {
            ...(p as unknown as Record<string, unknown>),
            permissionSetId: id,
          });
          return this.axios.post('/api/permset-system-permissions', body);
        })
      );
    },

    assign: async (id: string, userId: string): Promise<void> => {
      const body = toJsonApiBody('user-permission-sets', {
        permissionSetId: id,
        userId,
      });
      await this.axios.post('/api/user-permission-sets', body);
    },

    unassign: async (id: string, userId: string): Promise<void> => {
      // Find the assignment record, then delete
      const response = await this.axios.get(
        `/api/user-permission-sets?filter[permissionSetId][eq]=${encodeURIComponent(id)}&filter[userId][eq]=${encodeURIComponent(userId)}`
      );
      const assignments = unwrapJsonApiList<{ id: string }>(response.data);
      if (assignments.length > 0) {
        await this.axios.delete(`/api/user-permission-sets/${assignments[0].id}`);
      }
    },
  };

  // ---------------------------------------------------------------------------
  // Sharing (OWD, rules, record shares)
  // ---------------------------------------------------------------------------

  readonly sharing = {
    getOwd: async (collectionId: string): Promise<OrgWideDefault> => {
      const response = await this.axios.get(
        `/api/org-wide-defaults?filter[collectionId][eq]=${encodeURIComponent(collectionId)}`
      );
      const items = unwrapJsonApiList<OrgWideDefault>(response.data);
      return items[0] ?? ({} as OrgWideDefault);
    },

    setOwd: async (collectionId: string, request: SetOwdRequest): Promise<OrgWideDefault> => {
      // Try to find existing OWD, then update or create
      const existing = await this.axios.get(
        `/api/org-wide-defaults?filter[collectionId][eq]=${encodeURIComponent(collectionId)}`
      );
      const items = unwrapJsonApiList<OrgWideDefault>(existing.data);
      if (items.length > 0) {
        const body = toJsonApiBody(
          'org-wide-defaults',
          request as unknown as Record<string, unknown>,
          items[0].id
        );
        const response = await this.axios.patch(`/api/org-wide-defaults/${items[0].id}`, body);
        return unwrapJsonApiResource<OrgWideDefault>(response.data);
      }
      const body = toJsonApiBody('org-wide-defaults', {
        ...(request as unknown as Record<string, unknown>),
        collectionId,
      });
      const response = await this.axios.post('/api/org-wide-defaults', body);
      return unwrapJsonApiResource<OrgWideDefault>(response.data);
    },

    listOwds: async (): Promise<OrgWideDefault[]> => {
      const response = await this.axios.get('/api/org-wide-defaults');
      return unwrapJsonApiList<OrgWideDefault>(response.data);
    },

    listRules: async (collectionId: string): Promise<SharingRule[]> => {
      const response = await this.axios.get(
        `/api/sharing-rules?filter[collectionId][eq]=${encodeURIComponent(collectionId)}`
      );
      return unwrapJsonApiList<SharingRule>(response.data);
    },

    createRule: async (
      _collectionId: string,
      request: CreateSharingRuleRequest
    ): Promise<SharingRule> => {
      const body = toJsonApiBody('sharing-rules', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/sharing-rules', body);
      return unwrapJsonApiResource<SharingRule>(response.data);
    },

    updateRule: async (ruleId: string, request: UpdateSharingRuleRequest): Promise<SharingRule> => {
      const body = toJsonApiBody(
        'sharing-rules',
        request as unknown as Record<string, unknown>,
        ruleId
      );
      const response = await this.axios.patch(`/api/sharing-rules/${ruleId}`, body);
      return unwrapJsonApiResource<SharingRule>(response.data);
    },

    deleteRule: async (ruleId: string): Promise<void> => {
      await this.axios.delete(`/api/sharing-rules/${ruleId}`);
    },

    listRecordShares: async (collectionId: string, recordId: string): Promise<RecordShare[]> => {
      const response = await this.axios.get(
        `/api/record-shares?filter[collectionId][eq]=${encodeURIComponent(collectionId)}&filter[recordId][eq]=${encodeURIComponent(recordId)}`
      );
      return unwrapJsonApiList<RecordShare>(response.data);
    },
  };

  // ---------------------------------------------------------------------------
  // User groups
  // ---------------------------------------------------------------------------

  readonly groups = {
    list: async (): Promise<UserGroup[]> => {
      const response = await this.axios.get('/api/user-groups');
      return unwrapJsonApiList<UserGroup>(response.data);
    },

    get: async (id: string): Promise<UserGroup> => {
      const response = await this.axios.get(`/api/user-groups/${id}`);
      return unwrapJsonApiResource<UserGroup>(response.data);
    },

    create: async (request: CreateUserGroupRequest): Promise<UserGroup> => {
      const body = toJsonApiBody('user-groups', request as unknown as Record<string, unknown>);
      const response = await this.axios.post('/api/user-groups', body);
      return unwrapJsonApiResource<UserGroup>(response.data);
    },

    updateMembers: async (id: string, memberIds: string[]): Promise<UserGroup> => {
      // Update group by patching members
      const body = toJsonApiBody('user-groups', { memberIds }, id);
      const response = await this.axios.patch(`/api/user-groups/${id}`, body);
      return unwrapJsonApiResource<UserGroup>(response.data);
    },

    delete: async (id: string): Promise<void> => {
      await this.axios.delete(`/api/user-groups/${id}`);
    },
  };

  // ---------------------------------------------------------------------------
  // Audit
  // ---------------------------------------------------------------------------

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
      const filters: Record<string, string> = {};
      if (params?.section) filters['filter[section][eq]'] = params.section;
      if (params?.entityType) filters['filter[entityType][eq]'] = params.entityType;
      if (params?.userId) filters['filter[userId][eq]'] = params.userId;
      if (params?.from) filters['filter[createdAt][gte]'] = params.from;
      if (params?.to) filters['filter[createdAt][lte]'] = params.to;
      const qs = buildJsonApiParams({
        page: params?.page,
        size: params?.size,
        filters,
      });
      const response = await this.axios.get(`/api/setup-audit-entries${qs}`);
      const items = unwrapJsonApiList<SetupAuditTrailEntry>(response.data);
      const meta = extractMetadata(response.data);
      return toPage(items, meta);
    },

    getEntityHistory: async (
      entityType: string,
      entityId: string,
      page?: number,
      size?: number
    ): Promise<Page<SetupAuditTrailEntry>> => {
      const qs = buildJsonApiParams({
        page,
        size,
        filters: {
          'filter[entityType][eq]': entityType,
          'filter[entityId][eq]': entityId,
        },
      });
      const response = await this.axios.get(`/api/setup-audit-entries${qs}`);
      const items = unwrapJsonApiList<SetupAuditTrailEntry>(response.data);
      const meta = extractMetadata(response.data);
      return toPage(items, meta);
    },
  };

  // ---------------------------------------------------------------------------
  // Governor limits
  // ---------------------------------------------------------------------------

  readonly governorLimits = {
    getStatus: async (): Promise<GovernorLimitsStatus> => {
      const response = await this.axios.get('/api/governor-limits');
      const items = unwrapJsonApiList<GovernorLimitsStatus>(response.data);
      return items[0] ?? ({} as GovernorLimitsStatus);
    },
  };

  // ---------------------------------------------------------------------------
  // Role hierarchy
  // ---------------------------------------------------------------------------

  readonly roleHierarchy = {
    get: async (): Promise<RoleHierarchyNode[]> => {
      const response = await this.axios.get('/api/roles?sort=hierarchyLevel');
      return unwrapJsonApiList<RoleHierarchyNode>(response.data);
    },

    setParent: async (roleId: string, parentRoleId: string | null): Promise<RoleHierarchyNode> => {
      const body = toJsonApiBody('roles', { parentRoleId }, roleId);
      const response = await this.axios.patch(`/api/roles/${roleId}`, body);
      return unwrapJsonApiResource<RoleHierarchyNode>(response.data);
    },
  };

  // ---------------------------------------------------------------------------
  // Page layouts (already on /api/)
  // ---------------------------------------------------------------------------

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

    /**
     * Resolve layout for a collection by fetching assignments and picking the match.
     * Replaces the old /control/layouts/resolve endpoint.
     */
    resolve: async (
      collectionId: string,
      profileId?: string,
      recordTypeId?: string
    ): Promise<PageLayout> => {
      // Fetch all layout assignments for this collection
      const assignResponse = await this.axios.get(
        `/api/layout-assignments?filter[collectionId][eq]=${encodeURIComponent(collectionId)}`
      );
      const assignments = unwrapJsonApiList<
        LayoutAssignment & { layoutId: string; profileId?: string; recordTypeId?: string }
      >(assignResponse.data);

      // Find best match: profile + recordType > profile > default
      let match = assignments.find(
        (a) => a.profileId === profileId && a.recordTypeId === recordTypeId
      );
      if (!match && profileId) {
        match = assignments.find((a) => a.profileId === profileId && !a.recordTypeId);
      }
      if (!match) {
        match = assignments[0];
      }

      if (!match?.layoutId) {
        return {} as PageLayout;
      }

      // Fetch the resolved layout
      const layoutResponse = await this.axios.get(`/api/page-layouts/${match.layoutId}`);
      return unwrapJsonApiResource<PageLayout>(layoutResponse.data);
    },
  };

  // ---------------------------------------------------------------------------
  // List views (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Reports (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Dashboards (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Data export — graceful degradation
  // ---------------------------------------------------------------------------

  readonly dataExport = {
    exportCsv: (_request: ExportRequest): Promise<Blob> => {
      throw new Error('CSV export is temporarily unavailable');
    },

    exportXlsx: (_request: ExportRequest): Promise<Blob> => {
      throw new Error('XLSX export is temporarily unavailable');
    },
  };

  // ---------------------------------------------------------------------------
  // Email templates (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Workflow rules (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Approval processes (already on /api/)
  // ---------------------------------------------------------------------------

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

    listInstances: async (_tenantId: string): Promise<ApprovalInstance[]> => {
      const response = await this.axios.get('/api/approval-instances');
      return unwrapJsonApiList<ApprovalInstance>(response.data);
    },

    getPendingForUser: async (userId: string): Promise<ApprovalInstance[]> => {
      const response = await this.axios.get(
        `/api/approval-instances?filter[status][eq]=PENDING&filter[assignedTo][eq]=${encodeURIComponent(userId)}`
      );
      return unwrapJsonApiList<ApprovalInstance>(response.data);
    },
  };

  // ---------------------------------------------------------------------------
  // Flows (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Scheduled jobs (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Scripts (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Webhooks (already on /api/)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Connected apps (already on /api/)
  // ---------------------------------------------------------------------------

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

    rotateSecret: (_id: string): Promise<ConnectedAppCreatedResponse> => {
      throw new Error('Secret rotation is temporarily unavailable');
    },

    listTokens: async (id: string): Promise<ConnectedAppToken[]> => {
      const response = await this.axios.get(`/api/connected-apps/${id}/connected-app-tokens`);
      return unwrapJsonApiList<ConnectedAppToken>(response.data);
    },
  };

  // ---------------------------------------------------------------------------
  // Bulk jobs (already on /api/)
  // ---------------------------------------------------------------------------

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
      const body = toJsonApiBody('bulk-jobs', { status: 'ABORTED' }, id);
      const response = await this.axios.patch(`/api/bulk-jobs/${id}`, body);
      return unwrapJsonApiResource<BulkJob>(response.data);
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

  // ---------------------------------------------------------------------------
  // Composite API — graceful degradation
  // ---------------------------------------------------------------------------

  readonly composite = {
    execute: (_tenantId: string, _request: CompositeRequest): Promise<CompositeResponse> => {
      throw new Error('Composite API is temporarily unavailable');
    },
  };
}
