package io.kelta.worker.module;

import io.kelta.runtime.flow.ActionHandlerDescriptor;
import io.kelta.runtime.module.ModuleManifest;
import io.kelta.runtime.module.ModuleManifestParser;
import io.kelta.runtime.module.ModuleStore;
import io.kelta.runtime.module.TenantModuleData;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionHandlerRegistry;
import io.kelta.runtime.workflow.ActionResult;
import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveHookRegistry;
import io.kelta.runtime.workflow.module.KeltaModule;
import io.kelta.runtime.workflow.module.ModuleContext;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of runtime-loaded tenant modules.
 * <p>
 * Handles module installation, enabling, disabling, and uninstalling.
 * Coordinates with the {@link ActionHandlerRegistry} to register/unregister
 * tenant-scoped action handlers.
 * <p>
 * When a {@link ModuleJarService} is available, modules are loaded from their
 * JAR files via a sandboxed {@link SandboxedModuleClassLoader}. If no JAR service
 * is configured (e.g., S3 is disabled), stub handlers are used instead.
 *
 * @since 1.0.0
 */
public class RuntimeModuleManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeModuleManager.class);

    private final ModuleStore moduleStore;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final ModuleManifestParser manifestParser;
    private final ObjectMapper objectMapper;
    private final ModuleJarService jarService;
    private final ModuleContext moduleContext;
    private final ModuleSignatureVerifier signatureVerifier;
    private final BeforeSaveHookRegistry beforeSaveHookRegistry;
    private final ModuleCollectionProvisioner collectionProvisioner;

    /** Tracks which modules are loaded per tenant: tenantId -> Set<moduleId> */
    private final Map<String, Set<String>> loadedModules = new ConcurrentHashMap<>();

    /** Tracks active ClassLoaders for cleanup: "tenantId:moduleId" -> ClassLoader */
    private final Map<String, SandboxedModuleClassLoader> activeClassLoaders = new ConcurrentHashMap<>();

    /** Tracks loaded KeltaModule instances for lifecycle management */
    private final Map<String, KeltaModule> activeModuleInstances = new ConcurrentHashMap<>();

    /** The exact hook instances registered per load, keyed "tenantId:moduleId" — see LoadedModule. */
    private final Map<String, List<BeforeSaveHook>> registeredHooks = new ConcurrentHashMap<>();

    /**
     * What each load actually registered, keyed "tenantId:moduleId".
     *
     * <p>Held in memory so unloading never needs the database row. Uninstall deletes the row and
     * <em>then</em> publishes UNINSTALLED, so every pod except the one that served the request
     * sees the event after the row is already gone — see {@link #unloadModule(String, String)}.
     */
    private final Map<String, LoadedModule> loadedActionKeys = new ConcurrentHashMap<>();

    /**
     * The registry keys, hook instances, and cached JAR a single loaded module owns.
     *
     * <p>Hooks are held by instance because {@link BeforeSaveHookRegistry} removes them by
     * identity — a module's {@code getBeforeSaveHooks()} may build fresh objects on each call, so
     * unload must use the instances that were actually registered.
     */
    private record LoadedModule(Set<String> actionKeys, List<BeforeSaveHook> hooks, String s3Key) {
    }

    /**
     * Creates a RuntimeModuleManager with JAR loading support.
     */
    public RuntimeModuleManager(ModuleStore moduleStore,
                                 ActionHandlerRegistry actionHandlerRegistry,
                                 ObjectMapper objectMapper,
                                 ModuleJarService jarService,
                                 ModuleContext moduleContext,
                                 ModuleSignatureVerifier signatureVerifier,
                                 BeforeSaveHookRegistry beforeSaveHookRegistry,
                                 ModuleCollectionProvisioner collectionProvisioner) {
        this.moduleStore = Objects.requireNonNull(moduleStore);
        this.actionHandlerRegistry = Objects.requireNonNull(actionHandlerRegistry);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.manifestParser = new ModuleManifestParser(objectMapper);
        this.jarService = jarService;
        this.moduleContext = moduleContext;
        this.signatureVerifier = signatureVerifier;
        this.beforeSaveHookRegistry = beforeSaveHookRegistry;
        this.collectionProvisioner = collectionProvisioner;
    }

    /**
     * Creates a RuntimeModuleManager without hook registration or collection provisioning
     * (e.g. in tests that only exercise action handlers).
     */
    public RuntimeModuleManager(ModuleStore moduleStore,
                                 ActionHandlerRegistry actionHandlerRegistry,
                                 ObjectMapper objectMapper,
                                 ModuleJarService jarService,
                                 ModuleContext moduleContext,
                                 ModuleSignatureVerifier signatureVerifier) {
        this(moduleStore, actionHandlerRegistry, objectMapper, jarService, moduleContext,
            signatureVerifier, null, null);
    }

    /**
     * Creates a RuntimeModuleManager with JAR loading but no signature verifier
     * (e.g. in tests). Equivalent to a disabled verifier — installs are not gated.
     */
    public RuntimeModuleManager(ModuleStore moduleStore,
                                 ActionHandlerRegistry actionHandlerRegistry,
                                 ObjectMapper objectMapper,
                                 ModuleJarService jarService,
                                 ModuleContext moduleContext) {
        this(moduleStore, actionHandlerRegistry, objectMapper, jarService, moduleContext, null);
    }

    /**
     * Creates a RuntimeModuleManager without JAR loading support (stub-only mode).
     */
    public RuntimeModuleManager(ModuleStore moduleStore,
                                 ActionHandlerRegistry actionHandlerRegistry,
                                 ObjectMapper objectMapper) {
        this(moduleStore, actionHandlerRegistry, objectMapper, null, null, null);
    }

    /**
     * Installs a module for a tenant.
     * Parses the manifest, persists metadata, and creates action records.
     *
     * @param tenantId    the tenant ID
     * @param manifestJson the module manifest JSON
     * @param sourceUrl   the original download URL
     * @param checksum    SHA-256 checksum
     * @param jarSizeBytes JAR file size
     * @param installedBy the user who installed the module
     * @return the persisted module data
     */
    public TenantModuleData installModule(String tenantId, String manifestJson,
                                           String sourceUrl, String checksum,
                                           Long jarSizeBytes, String installedBy) {
        ModuleManifest manifest = manifestParser.parse(manifestJson);

        // Check for existing installation
        Optional<TenantModuleData> existing = moduleStore.findByTenantAndModuleId(
            tenantId, manifest.id());
        if (existing.isPresent()) {
            throw new IllegalStateException(
                "Module '" + manifest.id() + "' is already installed for tenant " + tenantId);
        }

        String id = UUID.randomUUID().toString();
        TenantModuleData data = new TenantModuleData(
            id, tenantId, manifest.id(), manifest.name(), manifest.version(),
            manifest.description(), sourceUrl, checksum, jarSizeBytes,
            manifest.moduleClass(), manifestJson,
            TenantModuleData.STATUS_INSTALLED, installedBy,
            null, null, null, List.of()
        );

        moduleStore.createModule(data);

        // Create action records from manifest
        List<TenantModuleData.TenantModuleActionData> actions = new ArrayList<>();
        for (ModuleManifest.ActionHandlerManifest handler : manifest.actionHandlers()) {
            actions.add(new TenantModuleData.TenantModuleActionData(
                UUID.randomUUID().toString(), id, handler.key(), handler.name(),
                handler.category(), handler.description(), handler.configSchema(),
                handler.inputSchema(), handler.outputSchema()
            ));
        }
        if (!actions.isEmpty()) {
            moduleStore.createActions(actions);
        }

        provisionCollections(tenantId, manifest);

        log.info("Installed module '{}' v{} for tenant {} with {} action handlers",
            manifest.name(), manifest.version(), tenantId, actions.size());

        return moduleStore.findById(id).orElse(data);
    }

    /**
     * Installs a module with its JAR file.
     * Uploads the JAR to S3 and persists the S3 key.
     *
     * @param tenantId     the tenant ID
     * @param manifestJson the module manifest JSON
     * @param jarBytes     the module JAR file bytes
     * @param installedBy  the user who installed the module
     * @return the persisted module data
     */
    public TenantModuleData installModuleWithJar(String tenantId, String manifestJson,
                                                   byte[] jarBytes, String installedBy) {
        return installModuleWithJar(tenantId, manifestJson, jarBytes, installedBy, null);
    }

    /**
     * Installs a module with its JAR file, verifying the JAR's publisher signature
     * first (Rec 9). When signature verification is enabled
     * ({@code kelta.modules.signing.public-key} set), {@code signatureBase64} must
     * be a valid detached signature over the JAR bytes or the install is rejected
     * before anything is uploaded or persisted.
     *
     * @param signatureBase64 detached base64 signature over the JAR bytes (may be
     *                        {@code null} when verification is disabled)
     * @throws ModuleSignatureException if signature verification fails
     */
    public TenantModuleData installModuleWithJar(String tenantId, String manifestJson,
                                                   byte[] jarBytes, String installedBy,
                                                   String signatureBase64) {
        if (jarService == null) {
            throw new IllegalStateException("JAR upload requires S3 storage to be enabled");
        }

        // Authenticity gate — reject before any S3 upload or DB write. Verified against the
        // INSTALLING TENANT's own signing keys, so a JAR signed for one tenant cannot be
        // installed into another. Null when the tenant trusts no key and signing is not
        // required; a ModuleSignatureException otherwise.
        String signingKeyFingerprint = null;
        if (signatureVerifier != null) {
            signingKeyFingerprint = signatureVerifier.verify(tenantId, jarBytes, signatureBase64);
        }

        ModuleManifest manifest = manifestParser.parse(manifestJson);

        String checksum = ModuleJarService.sha256(jarBytes);
        String s3Key = jarService.uploadJar(tenantId, manifest.id(), manifest.version(), jarBytes);

        // Check for existing installation
        Optional<TenantModuleData> existing = moduleStore.findByTenantAndModuleId(
            tenantId, manifest.id());
        if (existing.isPresent()) {
            throw new IllegalStateException(
                "Module '" + manifest.id() + "' is already installed for tenant " + tenantId);
        }

        String id = UUID.randomUUID().toString();
        TenantModuleData data = new TenantModuleData(
            id, tenantId, manifest.id(), manifest.name(), manifest.version(),
            manifest.description(), s3Key, checksum, (long) jarBytes.length,
            manifest.moduleClass(), manifestJson,
            TenantModuleData.STATUS_INSTALLED, installedBy,
            null, null, s3Key, List.of()
        );

        moduleStore.createModule(data);
        if (signatureBase64 != null && !signatureBase64.isBlank()) {
            // Keep the verified signature so every subsequent load can re-verify
            // the downloaded JAR (defense-in-depth vs S3 tamper), and the key that
            // verified it so a rotation can tell what still depends on that key.
            moduleStore.saveJarSignature(id, signatureBase64, signingKeyFingerprint);
        }

        // Create action records from manifest
        List<TenantModuleData.TenantModuleActionData> actions = new ArrayList<>();
        for (ModuleManifest.ActionHandlerManifest handler : manifest.actionHandlers()) {
            actions.add(new TenantModuleData.TenantModuleActionData(
                UUID.randomUUID().toString(), id, handler.key(), handler.name(),
                handler.category(), handler.description(), handler.configSchema(),
                handler.inputSchema(), handler.outputSchema()
            ));
        }
        if (!actions.isEmpty()) {
            moduleStore.createActions(actions);
        }

        provisionCollections(tenantId, manifest);

        log.info("Installed module '{}' v{} for tenant {} with JAR (s3Key={}, {} bytes)",
            manifest.name(), manifest.version(), tenantId, s3Key, jarBytes.length);

        return moduleStore.findById(id).orElse(data);
    }

    /**
     * Creates the collections the manifest declares, in the installing tenant.
     *
     * <p>Failure is logged, not thrown: the module row and its action records are already
     * persisted at this point, so propagating would leave a half-installed module the admin
     * cannot see or uninstall. The operator sees the error and can retry by reinstalling —
     * provisioning skips collections that already exist, so a retry is safe.
     */
    private void provisionCollections(String tenantId, ModuleManifest manifest) {
        if (collectionProvisioner == null || manifest.collections().isEmpty()) {
            return;
        }
        try {
            List<String> created = collectionProvisioner.provision(tenantId, manifest.collections());
            log.info("Module '{}' provisioned {} collection(s) for tenant {}: {}",
                manifest.id(), created.size(), tenantId, created);
        } catch (RuntimeException e) {
            log.error("Module '{}' failed to provision collections for tenant {}: {}",
                manifest.id(), tenantId, e.getMessage(), e);
        }
    }

    /**
     * Enables a module, registering its action handlers.
     *
     * @param tenantId the tenant ID
     * @param moduleId the module identifier
     */
    public void enableModule(String tenantId, String moduleId) {
        TenantModuleData module = moduleStore.findByTenantAndModuleId(tenantId, moduleId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Module '" + moduleId + "' not found for tenant " + tenantId));

        if (TenantModuleData.STATUS_ACTIVE.equals(module.status())) {
            log.debug("Module '{}' already active for tenant {}", moduleId, tenantId);
            return;
        }

        moduleStore.updateStatus(module.id(), TenantModuleData.STATUS_ACTIVE);
        loadModule(tenantId, module);
        log.info("Enabled module '{}' for tenant {}", moduleId, tenantId);
    }

    /**
     * Disables a module, unregistering its action handlers.
     *
     * @param tenantId the tenant ID
     * @param moduleId the module identifier
     */
    public void disableModule(String tenantId, String moduleId) {
        TenantModuleData module = moduleStore.findByTenantAndModuleId(tenantId, moduleId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Module '" + moduleId + "' not found for tenant " + tenantId));

        moduleStore.updateStatus(module.id(), TenantModuleData.STATUS_DISABLED);
        unloadModule(tenantId, module);
        log.info("Disabled module '{}' for tenant {}", moduleId, tenantId);
    }

    /**
     * Uninstalls a module entirely.
     *
     * @param tenantId the tenant ID
     * @param moduleId the module identifier
     */
    public void uninstallModule(String tenantId, String moduleId) {
        TenantModuleData module = moduleStore.findByTenantAndModuleId(tenantId, moduleId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Module '" + moduleId + "' not found for tenant " + tenantId));

        unloadModule(tenantId, module);

        // Clean up S3 JAR if present
        if (module.s3Key() != null && jarService != null) {
            try {
                jarService.deleteJar(module.s3Key());
            } catch (Exception e) {
                log.warn("Failed to delete JAR from S3 for module '{}': {}",
                    moduleId, e.getMessage());
            }
        }

        moduleStore.deleteModule(module.id());
        log.info("Uninstalled module '{}' from tenant {}", moduleId, tenantId);
    }

    /**
     * Loads a module's handlers into the registry (called on enable or pod startup).
     * <p>
     * If the module has an S3 JAR key and JAR service is available, loads real handlers
     * from the JAR using a sandboxed ClassLoader. Otherwise, falls back to stub handlers.
     * <p>
     * Idempotent — no-op if already loaded.
     *
     * @param tenantId the tenant ID
     * @param module the module data
     */
    public void loadModule(String tenantId, TenantModuleData module) {
        Set<String> loaded = loadedModules.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet());
        if (loaded.contains(module.moduleId())) {
            log.debug("Module '{}' already loaded for tenant {}", module.moduleId(), tenantId);
            return;
        }

        if (module.s3Key() != null && jarService != null) {
            loadFromJar(tenantId, module);
        } else {
            loadWithStubs(tenantId, module);
        }

        loaded.add(module.moduleId());
        // Capture what was registered while the row is still in hand; unload works from this
        // rather than re-reading the database, which may no longer have the row by then.
        Set<String> registered = new HashSet<>();
        for (var action : module.actions()) {
            registered.add(action.actionKey());
        }
        String key = tenantId + ":" + module.moduleId();
        KeltaModule instance = activeModuleInstances.get(key);
        if (instance != null) {
            for (ActionHandler handler : instance.getActionHandlers()) {
                registered.add(handler.getActionTypeKey());
            }
        }
        loadedActionKeys.put(key,
            new LoadedModule(registered, registeredHooks.getOrDefault(key, List.of()),
                module.s3Key()));

        log.info("Loaded module '{}' v{} with {} handlers for tenant {}",
            module.name(), module.version(), module.actions().size(), tenantId);
    }

    /**
     * Loads module handlers from the JAR via a sandboxed ClassLoader.
     */
    private void loadFromJar(String tenantId, TenantModuleData module) {
        String classLoaderKey = tenantId + ":" + module.moduleId();
        try {
            URL jarUrl = jarService.downloadJarToCache(module.s3Key());
            verifyDownloadedJar(module, jarUrl);
            SandboxedModuleClassLoader classLoader = new SandboxedModuleClassLoader(
                module.moduleId(), jarUrl, getClass().getClassLoader());

            // Load the KeltaModule implementation class
            @SuppressWarnings("unchecked")
            Class<? extends KeltaModule> moduleClass = (Class<? extends KeltaModule>)
                classLoader.loadClass(module.moduleClass());

            KeltaModule keltaModule = moduleClass.getDeclaredConstructor().newInstance();

            // Provide restricted module context and initialize
            if (moduleContext != null) {
                keltaModule.onStartup(moduleContext);
            }

            // Register real action handlers from the module
            List<ActionHandler> handlers = keltaModule.getActionHandlers();
            for (ActionHandler handler : handlers) {
                actionHandlerRegistry.registerTenantHandler(tenantId, handler);
            }

            // Register before-save hooks, tenant-scoped so a module installed by one tenant
            // never fires on another tenant's records.
            List<BeforeSaveHook> hooks = registerTenantHooks(tenantId, keltaModule);

            activeClassLoaders.put(classLoaderKey, classLoader);
            activeModuleInstances.put(classLoaderKey, keltaModule);
            registeredHooks.put(classLoaderKey, hooks);

            log.info("Loaded module '{}' from JAR with {} real handlers and {} hooks for tenant {}",
                module.moduleId(), handlers.size(), hooks.size(), tenantId);

        } catch (Exception e) {
            log.warn("Failed to load module '{}' from JAR for tenant {}: {}. Falling back to stubs.",
                module.moduleId(), tenantId, e.getMessage(), e);

            // Clean up partial ClassLoader on failure
            SandboxedModuleClassLoader cl = activeClassLoaders.remove(classLoaderKey);
            if (cl != null) {
                try { cl.close(); } catch (IOException ignored) {}
            }
            activeModuleInstances.remove(classLoaderKey);
            // Hooks may have been registered before the failure; strip them so a half-loaded
            // module cannot keep vetoing saves after falling back to stubs.
            List<BeforeSaveHook> partialHooks = registeredHooks.remove(classLoaderKey);
            if (partialHooks != null && beforeSaveHookRegistry != null) {
                beforeSaveHookRegistry.removeTenantHooks(tenantId, partialHooks);
            }

            // Fall back to stubs
            loadWithStubs(tenantId, module);
        }
    }

    /**
     * Re-verifies a JAR downloaded from S3 before it is classloaded
     * (defense-in-depth vs storage tamper — the install-time gate already ran):
     * the bytes must match the checksum persisted at install, and when
     * signature verification is enabled the install-time publisher signature
     * must still verify. Failure throws — the caller falls back to inert
     * stub handlers, never executing unverified code.
     */
    private void verifyDownloadedJar(TenantModuleData module, URL jarUrl) throws Exception {
        byte[] jarBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(jarUrl.toURI()));

        String expectedChecksum = module.jarChecksum();
        if (expectedChecksum != null && !expectedChecksum.isBlank()) {
            String actual = ModuleJarService.sha256(jarBytes);
            if (!expectedChecksum.equals(actual)) {
                throw new ModuleSignatureException(
                    "Module '" + module.moduleId() + "' JAR checksum mismatch on load "
                        + "(expected " + expectedChecksum + ", got " + actual
                        + ") — possible storage tamper, refusing to load");
            }
        }

        if (signatureVerifier != null && signatureVerifier.isEnabledFor(module.tenantId())) {
            String signature = moduleStore.findJarSignature(module.id()).orElse(null);
            // verify() throws when the signature is missing or invalid —
            // modules installed before signing was enabled must be re-installed
            // with a signature once a public key is configured.
            //
            // Re-verified against the tenant's keys AS THEY ARE NOW, not against the key
            // recorded at install: that is what lets a rotation be additive. The flip side is
            // that retiring a key whose modules were never re-signed makes them fail here and
            // fall back to stubs — see jar_signature_key_fingerprint and
            // GET /api/modules/signing-keys, which reports the dependent module count.
            signatureVerifier.verify(module.tenantId(), jarBytes, signature);
        }
    }

    /**
     * Parses a stored module manifest. Exposed so callers can read declarative fields
     * (e.g. {@code uiBundlePath}) without keeping their own parser instance.
     *
     * @throws ModuleManifestParser.ModuleManifestException if the manifest is unreadable
     */
    public ModuleManifest parseManifest(String manifestJson) {
        return manifestParser.parse(manifestJson);
    }

    /**
     * Dispatches a raw inbound webhook to the {@code webhookHandlerKey} the module's manifest
     * names.
     *
     * <p><b>The platform authenticates nothing here.</b> The route is unauthenticated by design —
     * a payment processor or other external system cannot present a platform JWT — so the
     * handler owns its own trust anchor, typically an HMAC over the raw body verified against a
     * credential the module resolves itself. The {@code tenantId} in the path is untrusted input:
     * it selects which tenant's module (and therefore which secret) to dispatch to, nothing more.
     *
     * <p>Returns empty when the tenant has no such ACTIVE module, when that module declares no
     * webhook handler, or when its handler is not registered. All three collapse to one outcome
     * so an unauthenticated caller cannot tell them apart and enumerate a tenant's modules.
     *
     * @param tenantId  the tenant named in the path (untrusted)
     * @param moduleId  the module named in the path (untrusted)
     * @param rawBody   the unparsed request body — handlers that verify a body signature MUST use
     *                  this exact string, since re-serializing changes the bytes the HMAC covers
     * @param headers   inbound request headers (signature headers live here)
     * @return the handler's result, or empty if nothing was dispatched
     */
    public Optional<ActionResult> dispatchWebhook(String tenantId, String moduleId,
                                                  String rawBody, Map<String, String> headers) {
        Optional<TenantModuleData> found = moduleStore.findByTenantAndModuleId(tenantId, moduleId);
        if (found.isEmpty() || !TenantModuleData.STATUS_ACTIVE.equals(found.get().status())) {
            return Optional.empty();
        }

        String handlerKey;
        try {
            handlerKey = manifestParser.parse(found.get().manifest()).webhookHandlerKey();
        } catch (RuntimeException e) {
            log.warn("Module '{}' of tenant {} has an unreadable manifest — cannot dispatch webhook",
                moduleId, tenantId);
            return Optional.empty();
        }
        if (handlerKey == null || handlerKey.isBlank()) {
            return Optional.empty();
        }

        Optional<ActionHandler> handler = actionHandlerRegistry.getHandler(tenantId, handlerKey);
        if (handler.isEmpty()) {
            log.warn("Module '{}' of tenant {} declares webhook handler '{}' which is not "
                + "registered — the module may have fallen back to stubs", moduleId, tenantId, handlerKey);
            return Optional.empty();
        }

        ActionContext context = ActionContext.builder()
            .tenantId(tenantId)
            .resolvedData(Map.of(
                "rawBody", rawBody == null ? "" : rawBody,
                "headers", headers == null ? Map.of() : headers,
                "moduleId", moduleId))
            .build();
        return Optional.of(handler.get().execute(context));
    }

    /**
     * Reads a module's UI bundle from its verified JAR.
     *
     * <p>Streams the {@code uiBundlePath} resource out of the JAR the tenant already has
     * installed. That JAR passed signature verification at install and checksum + signature
     * re-verification on load, so this adds no new trust boundary — but it does serve
     * publisher-authored JavaScript same-origin to the admin UI, which is exactly what the
     * signature gate exists to make safe.
     *
     * <p>Empty when the tenant has no such ACTIVE module, the module declares no bundle, JAR
     * loading is unavailable, or the resource is absent from the JAR.
     */
    public Optional<byte[]> readUiBundle(String tenantId, String moduleId) {
        Optional<TenantModuleData> found = moduleStore.findByTenantAndModuleId(tenantId, moduleId);
        if (found.isEmpty() || !TenantModuleData.STATUS_ACTIVE.equals(found.get().status())) {
            return Optional.empty();
        }
        TenantModuleData module = found.get();
        if (module.s3Key() == null || jarService == null) {
            return Optional.empty();
        }

        String bundlePath;
        try {
            bundlePath = manifestParser.parse(module.manifest()).uiBundlePath();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (bundlePath == null || bundlePath.isBlank()) {
            return Optional.empty();
        }

        try {
            URL jarUrl = jarService.downloadJarToCache(module.s3Key());
            // Same gate the classloader path runs: never serve bytes out of a JAR whose
            // checksum or signature no longer verifies.
            verifyDownloadedJar(module, jarUrl);
            try (java.util.jar.JarFile jar =
                     new java.util.jar.JarFile(java.nio.file.Path.of(jarUrl.toURI()).toFile())) {
                java.util.jar.JarEntry entry = jar.getJarEntry(bundlePath);
                if (entry == null) {
                    log.warn("Module '{}' of tenant {} declares uiBundlePath '{}' which is not in "
                        + "its JAR", moduleId, tenantId, bundlePath);
                    return Optional.empty();
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    return Optional.of(in.readAllBytes());
                }
            }
        } catch (Exception e) {
            log.warn("Could not read UI bundle for module '{}' of tenant {}: {}",
                moduleId, tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Registers a module's before-save hooks against the installing tenant, returning the exact
     * instances registered so unload can remove them by identity. Returns empty when no hook
     * registry is wired (stub-only test setups) — hooks are then simply not active.
     */
    private List<BeforeSaveHook> registerTenantHooks(String tenantId, KeltaModule keltaModule) {
        if (beforeSaveHookRegistry == null) {
            return List.of();
        }
        List<BeforeSaveHook> hooks = List.copyOf(keltaModule.getBeforeSaveHooks());
        for (BeforeSaveHook hook : hooks) {
            beforeSaveHookRegistry.registerTenantHook(tenantId, hook);
        }
        return hooks;
    }

    /**
     * Loads stub handlers from manifest metadata (no JAR available).
     */
    private void loadWithStubs(String tenantId, TenantModuleData module) {
        for (var action : module.actions()) {
            ActionHandler stubHandler = createStubHandler(action, module);
            actionHandlerRegistry.registerTenantHandler(tenantId, stubHandler);
        }
        log.debug("Loaded module '{}' with stub handlers for tenant {}", module.moduleId(), tenantId);
    }

    /**
     * Unloads a module's handlers from the registry.
     * Idempotent — no-op if not loaded.
     *
     * @param tenantId the tenant ID
     * @param module the module data
     */
    public void unloadModule(String tenantId, TenantModuleData module) {
        unloadModule(tenantId, module.moduleId());
    }

    /**
     * Unloads a module's handlers using only its identifier.
     * Idempotent — no-op if not loaded.
     *
     * <p>Deliberately does <strong>not</strong> read the module row. Uninstall deletes the row and
     * only then publishes UNINSTALLED, so every pod other than the one that served the request
     * processes the event against a row that no longer exists. The previous implementation looked
     * the row up, found nothing, and returned without unloading — leaving {@code loadedModules}
     * holding the id. A subsequent reinstall then hit the "already loaded" early return in
     * {@link #loadModule} and silently registered nothing, so the module ran on the request-serving
     * pod and was missing from every other one while {@code /api/modules} reported ACTIVE. A flow
     * step would fail ResourceNotFound on some pods and not others.
     *
     * @param tenantId the tenant ID
     * @param moduleId the module identifier from the manifest
     */
    public void unloadModule(String tenantId, String moduleId) {
        Set<String> loaded = loadedModules.get(tenantId);
        if (loaded == null || !loaded.contains(moduleId)) {
            log.debug("Module '{}' not loaded for tenant {}", moduleId, tenantId);
            return;
        }

        String classLoaderKey = tenantId + ":" + moduleId;
        LoadedModule record = loadedActionKeys.remove(classLoaderKey);
        Set<String> actionKeys = new HashSet<>(
            record != null ? record.actionKeys() : Set.<String>of());

        // Also remove any real handlers registered by the KeltaModule
        KeltaModule keltaModule = activeModuleInstances.remove(classLoaderKey);
        if (keltaModule != null) {
            for (ActionHandler handler : keltaModule.getActionHandlers()) {
                actionKeys.add(handler.getActionTypeKey());
            }
        }

        actionHandlerRegistry.removeTenantHandlers(tenantId, actionKeys);

        // Remove the tenant-scoped before-save hooks this load registered. Taken from the
        // in-memory record rather than by re-calling getBeforeSaveHooks(), which may return
        // fresh instances the registry would not match by identity.
        List<BeforeSaveHook> hooks = registeredHooks.remove(classLoaderKey);
        if (hooks == null && record != null) {
            hooks = record.hooks();
        }
        if (hooks != null && !hooks.isEmpty() && beforeSaveHookRegistry != null) {
            beforeSaveHookRegistry.removeTenantHooks(tenantId, hooks);
        }

        // Close the ClassLoader
        SandboxedModuleClassLoader classLoader = activeClassLoaders.remove(classLoaderKey);
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                log.warn("Failed to close ClassLoader for module '{}': {}", moduleId, e.getMessage());
            }
        }

        // Evict JAR from local cache
        if (record != null && record.s3Key() != null && jarService != null) {
            jarService.evictFromCache(record.s3Key());
        }

        loaded.remove(moduleId);
        if (loaded.isEmpty()) {
            loadedModules.remove(tenantId);
        }
        log.info("Unloaded module '{}' for tenant {}", moduleId, tenantId);
    }

    /**
     * Loads all active modules on pod startup.
     */
    public void loadAllActiveModules() {
        List<TenantModuleData> activeModules = moduleStore.findAllActive();
        log.info("Loading {} active runtime modules on startup", activeModules.size());
        for (TenantModuleData module : activeModules) {
            try {
                loadModule(module.tenantId(), module);
            } catch (Exception e) {
                log.error("Failed to load module '{}' for tenant {}: {}",
                    module.moduleId(), module.tenantId(), e.getMessage(), e);
                moduleStore.updateStatus(module.id(), TenantModuleData.STATUS_FAILED);
            }
        }
    }

    /**
     * Lists all modules for a tenant.
     */
    public List<TenantModuleData> listModules(String tenantId) {
        return moduleStore.findByTenant(tenantId);
    }

    /**
     * Checks if a module is loaded for a tenant.
     */
    public boolean isLoaded(String tenantId, String moduleId) {
        Set<String> loaded = loadedModules.get(tenantId);
        return loaded != null && loaded.contains(moduleId);
    }

    /**
     * Checks if JAR-based loading is available.
     */
    public boolean isJarLoadingEnabled() {
        return jarService != null;
    }

    /**
     * Creates a stub action handler from manifest metadata.
     * Used when no JAR is available for real ClassLoader-based loading.
     */
    private ActionHandler createStubHandler(TenantModuleData.TenantModuleActionData action,
                                             TenantModuleData module) {
        return new ActionHandler() {
            @Override
            public String getActionTypeKey() {
                return action.actionKey();
            }

            @Override
            public ActionResult execute(ActionContext context) {
                log.info("Executing runtime module handler '{}' from module '{}' v{} (stub mode)",
                    action.actionKey(), module.name(), module.version());
                return ActionResult.success(Map.of(
                    "handler", action.actionKey(),
                    "module", module.moduleId(),
                    "status", "EXECUTED",
                    "mode", "stub"
                ));
            }

            @Override
            public ActionHandlerDescriptor getDescriptor() {
                return new ActionHandlerDescriptor() {
                    @Override public String getConfigSchema() { return action.configSchema(); }
                    @Override public String getInputSchema() { return action.inputSchema(); }
                    @Override public String getOutputSchema() { return action.outputSchema(); }
                    @Override public String getDisplayName() { return action.name(); }
                    @Override public String getCategory() { return action.category(); }
                    @Override public String getDescription() { return action.description(); }
                };
            }
        };
    }
}
