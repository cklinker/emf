package com.emf.controlplane.service;

import com.emf.controlplane.dto.ExportPackageRequest;
import com.emf.controlplane.dto.ImportPackageRequest;
import com.emf.controlplane.dto.ImportPreviewDto;
import com.emf.controlplane.dto.ImportResultDto;
import com.emf.controlplane.dto.PackageDto;
import com.emf.controlplane.entity.*;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.*;
import com.emf.controlplane.tenant.TenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing configuration packages.
 * Handles export and import operations for environment promotion.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>6.1: Export selected configuration items to a package</li>
 *   <li>6.2: Import package with dry-run mode to preview changes</li>
 *   <li>6.3: Apply package import and persist changes</li>
 *   <li>6.4: Validate package format and content</li>
 *   <li>6.5: Track package history in database</li>
 * </ul>
 */
@Service
public class PackageService {

    private static final Logger log = LoggerFactory.getLogger(PackageService.class);

    private final PackageRepository packageRepository;
    private final CollectionRepository collectionRepository;
    private final FieldRepository fieldRepository;
    private final OidcProviderRepository oidcProviderRepository;
    private final UiPageRepository uiPageRepository;
    private final UiMenuRepository uiMenuRepository;
    private final ObjectMapper objectMapper;

    public PackageService(
            PackageRepository packageRepository,
            CollectionRepository collectionRepository,
            FieldRepository fieldRepository,
            OidcProviderRepository oidcProviderRepository,
            UiPageRepository uiPageRepository,
            UiMenuRepository uiMenuRepository,
            ObjectMapper objectMapper) {
        this.packageRepository = packageRepository;
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
        this.oidcProviderRepository = oidcProviderRepository;
        this.uiPageRepository = uiPageRepository;
        this.uiMenuRepository = uiMenuRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Exports selected configuration items to a package.
     * The package is persisted to the database for tracking.
     *
     * @param request The export request with item selection
     * @return The exported package with all selected items
     * @throws ResourceNotFoundException if any selected item does not exist
     *
     * Validates: Requirements 6.1, 6.5
     */
    @Transactional
    public PackageDto exportPackage(ExportPackageRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Exporting package: {} v{} for tenant: {}", request.getName(), request.getVersion(), tenantId);

        // Create the package entity
        ConfigPackage pkg = new ConfigPackage(request.getName(), request.getVersion());
        pkg.setDescription(request.getDescription());
        if (tenantId != null) {
            pkg.setTenantId(tenantId);
        }

        // Create the DTO to hold exported data
        PackageDto dto = new PackageDto(
                pkg.getId(),
                pkg.getName(),
                pkg.getVersion(),
                pkg.getDescription(),
                null // createdAt will be set after save
        );

        // Export collections
        List<PackageDto.PackageCollectionDto> collections = exportCollections(request, pkg);
        dto.setCollections(collections);

        // Export OIDC providers
        List<PackageDto.PackageOidcProviderDto> oidcProviders = exportOidcProviders(request, pkg);
        dto.setOidcProviders(oidcProviders);

        // Export UI pages
        List<PackageDto.PackageUiPageDto> uiPages = exportUiPages(request, pkg);
        dto.setUiPages(uiPages);

        // Export UI menus
        List<PackageDto.PackageUiMenuDto> uiMenus = exportUiMenus(request, pkg);
        dto.setUiMenus(uiMenus);

        // Save the package to database for tracking
        pkg = packageRepository.save(pkg);
        dto.setId(pkg.getId());
        dto.setCreatedAt(pkg.getCreatedAt());

        log.info("Exported package {} with {} items", pkg.getId(), dto.getTotalItemCount());
        return dto;
    }

    /**
     * Imports a package with optional dry-run mode.
     * In dry-run mode, validates and previews changes without applying them.
     *
     * @param request The import request with package data
     * @param dryRun If true, only preview changes without applying
     * @return The import result with details of changes made or to be made
     *
     * Validates: Requirements 6.2, 6.3, 6.4, 6.5
     */
    @Transactional
    public ImportResultDto importPackage(ImportPackageRequest request, boolean dryRun) {
        PackageDto packageData = request.getPackageData();
        log.info("Importing package: {} v{} (dryRun={})",
                packageData.getName(), packageData.getVersion(), dryRun);

        // Validate the package
        List<String> validationErrors = validatePackage(packageData);
        if (!validationErrors.isEmpty()) {
            log.warn("Package validation failed with {} errors", validationErrors.size());
            return ImportResultDto.validationFailure(
                    packageData.getName(),
                    packageData.getVersion(),
                    validationErrors);
        }

        // Preview the import
        ImportPreviewDto preview = previewImport(packageData, request.getConflictStrategy());

        if (dryRun) {
            // Return preview as result without applying changes
            return convertPreviewToResult(preview, true);
        }

        // Apply the import
        return applyImport(packageData, request.getConflictStrategy(), preview);
    }

    /**
     * Validates the package format and content.
     *
     * @param packageData The package to validate
     * @return List of validation errors (empty if valid)
     *
     * Validates: Requirement 6.4
     */
    public List<String> validatePackage(PackageDto packageData) {
        List<String> errors = new ArrayList<>();

        if (packageData == null) {
            errors.add("Package data is required");
            return errors;
        }

        if (packageData.getName() == null || packageData.getName().isBlank()) {
            errors.add("Package name is required");
        }

        if (packageData.getVersion() == null || packageData.getVersion().isBlank()) {
            errors.add("Package version is required");
        }

        // Validate collections
        for (PackageDto.PackageCollectionDto collection : packageData.getCollections()) {
            if (collection.getName() == null || collection.getName().isBlank()) {
                errors.add("Collection name is required for collection ID: " + collection.getId());
            }
            for (PackageDto.PackageFieldDto field : collection.getFields()) {
                if (field.getName() == null || field.getName().isBlank()) {
                    errors.add("Field name is required for field ID: " + field.getId());
                }
                if (field.getType() == null || field.getType().isBlank()) {
                    errors.add("Field type is required for field: " + field.getName());
                }
            }
        }

        // Validate OIDC providers
        for (PackageDto.PackageOidcProviderDto provider : packageData.getOidcProviders()) {
            if (provider.getName() == null || provider.getName().isBlank()) {
                errors.add("OIDC provider name is required for provider ID: " + provider.getId());
            }
            if (provider.getIssuer() == null || provider.getIssuer().isBlank()) {
                errors.add("OIDC provider issuer is required for provider: " + provider.getName());
            }
            if (provider.getJwksUri() == null || provider.getJwksUri().isBlank()) {
                errors.add("OIDC provider JWKS URI is required for provider: " + provider.getName());
            }

            // Validate claim configurations (Requirements 9.3, 9.4)
            try {
                validateClaimPath(provider.getRolesClaim(), "rolesClaim");
            } catch (ValidationException e) {
                errors.add("OIDC provider " + provider.getName() + ": " + e.getMessage());
            }

            try {
                validateClaimPath(provider.getEmailClaim(), "emailClaim");
            } catch (ValidationException e) {
                errors.add("OIDC provider " + provider.getName() + ": " + e.getMessage());
            }

            try {
                validateClaimPath(provider.getUsernameClaim(), "usernameClaim");
            } catch (ValidationException e) {
                errors.add("OIDC provider " + provider.getName() + ": " + e.getMessage());
            }

            try {
                validateClaimPath(provider.getNameClaim(), "nameClaim");
            } catch (ValidationException e) {
                errors.add("OIDC provider " + provider.getName() + ": " + e.getMessage());
            }

            try {
                validateRolesMapping(provider.getRolesMapping());
            } catch (ValidationException e) {
                errors.add("OIDC provider " + provider.getName() + ": " + e.getMessage());
            }
        }

        // Validate UI pages
        for (PackageDto.PackageUiPageDto page : packageData.getUiPages()) {
            if (page.getName() == null || page.getName().isBlank()) {
                errors.add("UI page name is required for page ID: " + page.getId());
            }
            if (page.getPath() == null || page.getPath().isBlank()) {
                errors.add("UI page path is required for page: " + page.getName());
            }
        }

        // Validate UI menus
        for (PackageDto.PackageUiMenuDto menu : packageData.getUiMenus()) {
            if (menu.getName() == null || menu.getName().isBlank()) {
                errors.add("UI menu name is required for menu ID: " + menu.getId());
            }
        }

        return errors;
    }

    /**
     * Previews the import without applying changes.
     *
     * @param packageData The package to preview
     * @param conflictStrategy The strategy for handling conflicts
     * @return Preview of what would happen during import
     *
     * Validates: Requirement 6.2
     */
    public ImportPreviewDto previewImport(PackageDto packageData, ImportPackageRequest.ConflictStrategy conflictStrategy) {
        ImportPreviewDto preview = new ImportPreviewDto(packageData.getName(), packageData.getVersion());
        preview.setValid(true);

        // Preview collections
        for (PackageDto.PackageCollectionDto collection : packageData.getCollections()) {
            previewCollectionImport(collection, conflictStrategy, preview);
        }

        // Preview OIDC providers
        for (PackageDto.PackageOidcProviderDto provider : packageData.getOidcProviders()) {
            previewOidcProviderImport(provider, conflictStrategy, preview);
        }

        // Preview UI pages
        for (PackageDto.PackageUiPageDto page : packageData.getUiPages()) {
            previewUiPageImport(page, conflictStrategy, preview);
        }

        // Preview UI menus
        for (PackageDto.PackageUiMenuDto menu : packageData.getUiMenus()) {
            previewUiMenuImport(menu, conflictStrategy, preview);
        }

        preview.updateCounts();
        return preview;
    }

    /**
     * Applies the import and persists changes.
     *
     * @param packageData The package to import
     * @param conflictStrategy The strategy for handling conflicts
     * @param preview The preview of changes to apply
     * @return The import result
     *
     * Validates: Requirements 6.3, 6.5
     */
    private ImportResultDto applyImport(PackageDto packageData,
                                        ImportPackageRequest.ConflictStrategy conflictStrategy,
                                        ImportPreviewDto preview) {
        String tenantId = TenantContextHolder.getTenantId();

        // Create package record for tracking
        ConfigPackage pkg = new ConfigPackage(packageData.getName(), packageData.getVersion());
        pkg.setDescription(packageData.getDescription());
        if (tenantId != null) {
            pkg.setTenantId(tenantId);
        }

        ImportResultDto result = ImportResultDto.success(
                pkg.getId(), packageData.getName(), packageData.getVersion(), false);

        try {
            // Import collections
            for (PackageDto.PackageCollectionDto collection : packageData.getCollections()) {
                importCollection(collection, conflictStrategy, result, pkg);
            }

            // Import OIDC providers
            for (PackageDto.PackageOidcProviderDto provider : packageData.getOidcProviders()) {
                importOidcProvider(provider, conflictStrategy, result, pkg);
            }

            // Import UI pages
            for (PackageDto.PackageUiPageDto page : packageData.getUiPages()) {
                importUiPage(page, conflictStrategy, result, pkg);
            }

            // Import UI menus
            for (PackageDto.PackageUiMenuDto menu : packageData.getUiMenus()) {
                importUiMenu(menu, conflictStrategy, result, pkg);
            }

            // Save the package record
            pkg = packageRepository.save(pkg);
            result.setPackageId(pkg.getId());
            result.updateCounts();

            log.info("Import completed: {} created, {} updated, {} skipped, {} failed",
                    result.getCreatedCount(), result.getUpdatedCount(),
                    result.getSkippedCount(), result.getFailedCount());

        } catch (Exception e) {
            log.error("Import failed", e);
            result.setSuccess(false);
            result.setErrorMessage("Import failed: " + e.getMessage());
        }

        return result;
    }

    // ==================== Export Helper Methods ====================

    private List<PackageDto.PackageCollectionDto> exportCollections(ExportPackageRequest request, ConfigPackage pkg) {
        List<Collection> collections;
        if (request.isExportAll()) {
            collections = collectionRepository.findByActiveTrue();
        } else if (!request.getCollectionIds().isEmpty()) {
            collections = request.getCollectionIds().stream()
                    .map(id -> collectionRepository.findByIdAndActiveTrue(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Collection", id)))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }

        return collections.stream().map(collection -> {
            PackageDto.PackageCollectionDto dto = new PackageDto.PackageCollectionDto(
                    collection.getId(),
                    collection.getName(),
                    collection.getDescription(),
                    collection.getCurrentVersion()
            );

            // Export fields for this collection
            List<Field> fields = fieldRepository.findByCollectionIdAndActiveTrue(collection.getId());
            List<PackageDto.PackageFieldDto> fieldDtos = fields.stream()
                    .map(field -> new PackageDto.PackageFieldDto(
                            field.getId(),
                            field.getName(),
                            field.getType(),
                            field.isRequired(),
                            field.getDescription(),
                            field.getConstraints()
                    ))
                    .collect(Collectors.toList());
            dto.setFields(fieldDtos);

            // Add package item for tracking
            addPackageItem(pkg, "COLLECTION", collection.getId(), dto);

            return dto;
        }).collect(Collectors.toList());
    }

    private List<PackageDto.PackageOidcProviderDto> exportOidcProviders(ExportPackageRequest request, ConfigPackage pkg) {
        List<OidcProvider> providers;
        if (request.isExportAll()) {
            providers = oidcProviderRepository.findByActiveTrue();
        } else if (!request.getOidcProviderIds().isEmpty()) {
            providers = request.getOidcProviderIds().stream()
                    .map(id -> oidcProviderRepository.findByIdAndActiveTrue(id)
                            .orElseThrow(() -> new ResourceNotFoundException("OidcProvider", id)))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }

        return providers.stream().map(provider -> {
            PackageDto.PackageOidcProviderDto dto = PackageDto.PackageOidcProviderDto.fromEntity(provider);
            addPackageItem(pkg, "OIDC_PROVIDER", provider.getId(), dto);
            return dto;
        }).collect(Collectors.toList());
    }

    private List<PackageDto.PackageUiPageDto> exportUiPages(ExportPackageRequest request, ConfigPackage pkg) {
        List<UiPage> pages;
        if (request.isExportAll()) {
            pages = uiPageRepository.findByActiveTrue();
        } else if (!request.getUiPageIds().isEmpty()) {
            pages = request.getUiPageIds().stream()
                    .map(id -> uiPageRepository.findByIdAndActiveTrue(id)
                            .orElseThrow(() -> new ResourceNotFoundException("UiPage", id)))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }

        return pages.stream().map(page -> {
            PackageDto.PackageUiPageDto dto = new PackageDto.PackageUiPageDto(
                    page.getId(),
                    page.getName(),
                    page.getPath(),
                    page.getTitle(),
                    page.getConfig()
            );
            addPackageItem(pkg, "UI_PAGE", page.getId(), dto);
            return dto;
        }).collect(Collectors.toList());
    }

    private List<PackageDto.PackageUiMenuDto> exportUiMenus(ExportPackageRequest request, ConfigPackage pkg) {
        List<UiMenu> menus;
        if (request.isExportAll()) {
            menus = uiMenuRepository.findAll();
        } else if (!request.getUiMenuIds().isEmpty()) {
            menus = request.getUiMenuIds().stream()
                    .map(id -> uiMenuRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("UiMenu", id)))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }

        return menus.stream().map(menu -> {
            PackageDto.PackageUiMenuDto dto = new PackageDto.PackageUiMenuDto(
                    menu.getId(),
                    menu.getName(),
                    menu.getDescription()
            );

            // Export menu items
            List<PackageDto.PackageUiMenuItemDto> itemDtos = menu.getItems().stream()
                    .filter(UiMenuItem::isActive)
                    .map(item -> new PackageDto.PackageUiMenuItemDto(
                            item.getId(),
                            item.getLabel(),
                            item.getPath(),
                            item.getIcon(),
                            item.getDisplayOrder()
                    ))
                    .collect(Collectors.toList());
            dto.setItems(itemDtos);

            addPackageItem(pkg, "UI_MENU", menu.getId(), dto);
            return dto;
        }).collect(Collectors.toList());
    }

    private void addPackageItem(ConfigPackage pkg, String itemType, String itemId, Object content) {
        try {
            String contentJson = objectMapper.writeValueAsString(content);
            PackageItem item = new PackageItem(itemType, itemId, contentJson);
            pkg.addItem(item);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize package item: {} {}", itemType, itemId, e);
        }
    }

    // ==================== Preview Helper Methods ====================

    private void previewCollectionImport(PackageDto.PackageCollectionDto collection,
                                         ImportPackageRequest.ConflictStrategy strategy,
                                         ImportPreviewDto preview) {
        Optional<Collection> existing = collectionRepository.findByNameAndActiveTrue(collection.getName());
        addPreviewItem(preview, "COLLECTION", collection.getId(), collection.getName(), existing.isPresent(), strategy);
    }

    private void previewOidcProviderImport(PackageDto.PackageOidcProviderDto provider,
                                           ImportPackageRequest.ConflictStrategy strategy,
                                           ImportPreviewDto preview) {
        Optional<OidcProvider> existing = oidcProviderRepository.findByNameAndActiveTrue(provider.getName());
        addPreviewItem(preview, "OIDC_PROVIDER", provider.getId(), provider.getName(), existing.isPresent(), strategy);
    }

    private void previewUiPageImport(PackageDto.PackageUiPageDto page,
                                     ImportPackageRequest.ConflictStrategy strategy,
                                     ImportPreviewDto preview) {
        Optional<UiPage> existing = uiPageRepository.findByPathAndActiveTrue(page.getPath());
        addPreviewItem(preview, "UI_PAGE", page.getId(), page.getName(), existing.isPresent(), strategy);
    }

    private void previewUiMenuImport(PackageDto.PackageUiMenuDto menu,
                                     ImportPackageRequest.ConflictStrategy strategy,
                                     ImportPreviewDto preview) {
        Optional<UiMenu> existing = uiMenuRepository.findByName(menu.getName());
        addPreviewItem(preview, "UI_MENU", menu.getId(), menu.getName(), existing.isPresent(), strategy);
    }

    private void addPreviewItem(ImportPreviewDto preview, String itemType, String itemId,
                                String itemName, boolean exists, ImportPackageRequest.ConflictStrategy strategy) {
        ImportPreviewDto.ImportItemDto item = new ImportPreviewDto.ImportItemDto(itemType, itemId, itemName, null);

        if (!exists) {
            item.setAction(ImportPreviewDto.ImportAction.CREATE);
            preview.addItemToCreate(item);
        } else {
            switch (strategy) {
                case SKIP:
                    item.setAction(ImportPreviewDto.ImportAction.SKIP);
                    item.setReason("Item already exists");
                    preview.addItemToSkip(item);
                    break;
                case OVERWRITE:
                    item.setAction(ImportPreviewDto.ImportAction.UPDATE);
                    item.setReason("Item will be overwritten");
                    preview.addItemToUpdate(item);
                    break;
                case FAIL:
                    item.setAction(ImportPreviewDto.ImportAction.CONFLICT);
                    item.setReason("Item already exists and conflict strategy is FAIL");
                    preview.addConflict(item);
                    preview.setValid(false);
                    break;
            }
        }
    }

    // ==================== Import Helper Methods ====================

    private void importCollection(PackageDto.PackageCollectionDto collectionDto,
                                  ImportPackageRequest.ConflictStrategy strategy,
                                  ImportResultDto result,
                                  ConfigPackage pkg) {
        Optional<Collection> existing = collectionRepository.findByNameAndActiveTrue(collectionDto.getName());

        if (existing.isPresent()) {
            if (strategy == ImportPackageRequest.ConflictStrategy.SKIP) {
                result.addSkippedItem(new ImportResultDto.ImportedItemDto(
                        "COLLECTION", collectionDto.getId(), collectionDto.getName(), "SKIPPED", "Already exists"));
                return;
            } else if (strategy == ImportPackageRequest.ConflictStrategy.FAIL) {
                result.addFailedItem(new ImportResultDto.ImportedItemDto(
                        "COLLECTION", collectionDto.getId(), collectionDto.getName(), "FAILED", "Already exists"));
                return;
            }
            // OVERWRITE - update existing
            Collection collection = existing.get();
            collection.setDescription(collectionDto.getDescription());
            collectionRepository.save(collection);

            // Update fields
            importFields(collection, collectionDto.getFields());

            result.addUpdatedItem(new ImportResultDto.ImportedItemDto(
                    "COLLECTION", collection.getId(), collection.getName(), "UPDATED"));
            addPackageItem(pkg, "COLLECTION", collection.getId(), collectionDto);
        } else {
            // Create new collection
            Collection collection = new Collection(collectionDto.getName(), collectionDto.getDescription());
            collection.setCurrentVersion(1);
            collection.setActive(true);
            collection.setPath("/api/" + collectionDto.getName());
            collection = collectionRepository.save(collection);

            // Import fields
            importFields(collection, collectionDto.getFields());

            result.addCreatedItem(new ImportResultDto.ImportedItemDto(
                    "COLLECTION", collection.getId(), collection.getName(), "CREATED"));
            addPackageItem(pkg, "COLLECTION", collection.getId(), collectionDto);
        }
    }

    private void importFields(Collection collection, List<PackageDto.PackageFieldDto> fieldDtos) {
        for (PackageDto.PackageFieldDto fieldDto : fieldDtos) {
            Optional<Field> existing = fieldRepository.findByCollectionIdAndNameAndActiveTrue(
                    collection.getId(), fieldDto.getName());

            if (existing.isPresent()) {
                Field field = existing.get();
                field.setType(fieldDto.getType());
                field.setRequired(fieldDto.isRequired());
                field.setDescription(fieldDto.getDescription());
                field.setConstraints(fieldDto.getConstraints());
                fieldRepository.save(field);
            } else {
                Field field = new Field(fieldDto.getName(), fieldDto.getType());
                field.setCollection(collection);
                field.setRequired(fieldDto.isRequired());
                field.setDescription(fieldDto.getDescription());
                field.setConstraints(fieldDto.getConstraints());
                field.setActive(true);
                fieldRepository.save(field);
            }
        }
    }

    private void importOidcProvider(PackageDto.PackageOidcProviderDto providerDto,
                                    ImportPackageRequest.ConflictStrategy strategy,
                                    ImportResultDto result,
                                    ConfigPackage pkg) {
        // Apply defaults for missing claim fields (Requirement 9.3)
        applyClaimDefaults(providerDto);

        Optional<OidcProvider> existing = oidcProviderRepository.findByNameAndActiveTrue(providerDto.getName());

        if (existing.isPresent()) {
            if (strategy == ImportPackageRequest.ConflictStrategy.SKIP) {
                result.addSkippedItem(new ImportResultDto.ImportedItemDto(
                        "OIDC_PROVIDER", providerDto.getId(), providerDto.getName(), "SKIPPED", "Already exists"));
                return;
            } else if (strategy == ImportPackageRequest.ConflictStrategy.FAIL) {
                result.addFailedItem(new ImportResultDto.ImportedItemDto(
                        "OIDC_PROVIDER", providerDto.getId(), providerDto.getName(), "FAILED", "Already exists"));
                return;
            }
            // OVERWRITE - update existing
            OidcProvider provider = existing.get();
            provider.setIssuer(providerDto.getIssuer());
            provider.setJwksUri(providerDto.getJwksUri());
            provider.setClientId(providerDto.getClientId());
            provider.setAudience(providerDto.getAudience());
            provider.setRolesClaim(providerDto.getRolesClaim());
            provider.setRolesMapping(providerDto.getRolesMapping());
            provider.setEmailClaim(providerDto.getEmailClaim());
            provider.setUsernameClaim(providerDto.getUsernameClaim());
            provider.setNameClaim(providerDto.getNameClaim());
            oidcProviderRepository.save(provider);
            result.addUpdatedItem(new ImportResultDto.ImportedItemDto(
                    "OIDC_PROVIDER", provider.getId(), provider.getName(), "UPDATED"));
            addPackageItem(pkg, "OIDC_PROVIDER", provider.getId(), providerDto);
        } else {
            // Create new
            OidcProvider provider = new OidcProvider(providerDto.getName(), providerDto.getIssuer(), providerDto.getJwksUri());
            provider.setClientId(providerDto.getClientId());
            provider.setAudience(providerDto.getAudience());
            provider.setRolesClaim(providerDto.getRolesClaim());
            provider.setRolesMapping(providerDto.getRolesMapping());
            provider.setEmailClaim(providerDto.getEmailClaim());
            provider.setUsernameClaim(providerDto.getUsernameClaim());
            provider.setNameClaim(providerDto.getNameClaim());
            provider.setActive(true);
            provider = oidcProviderRepository.save(provider);
            result.addCreatedItem(new ImportResultDto.ImportedItemDto(
                    "OIDC_PROVIDER", provider.getId(), provider.getName(), "CREATED"));
            addPackageItem(pkg, "OIDC_PROVIDER", provider.getId(), providerDto);
        }
    }

    private void importUiPage(PackageDto.PackageUiPageDto pageDto,
                              ImportPackageRequest.ConflictStrategy strategy,
                              ImportResultDto result,
                              ConfigPackage pkg) {
        Optional<UiPage> existing = uiPageRepository.findByPathAndActiveTrue(pageDto.getPath());

        if (existing.isPresent()) {
            if (strategy == ImportPackageRequest.ConflictStrategy.SKIP) {
                result.addSkippedItem(new ImportResultDto.ImportedItemDto(
                        "UI_PAGE", pageDto.getId(), pageDto.getName(), "SKIPPED", "Already exists"));
                return;
            } else if (strategy == ImportPackageRequest.ConflictStrategy.FAIL) {
                result.addFailedItem(new ImportResultDto.ImportedItemDto(
                        "UI_PAGE", pageDto.getId(), pageDto.getName(), "FAILED", "Already exists"));
                return;
            }
            // OVERWRITE - update existing
            UiPage page = existing.get();
            page.setName(pageDto.getName());
            page.setTitle(pageDto.getTitle());
            page.setConfig(pageDto.getConfig());
            uiPageRepository.save(page);
            result.addUpdatedItem(new ImportResultDto.ImportedItemDto(
                    "UI_PAGE", page.getId(), page.getName(), "UPDATED"));
            addPackageItem(pkg, "UI_PAGE", page.getId(), pageDto);
        } else {
            // Create new
            UiPage page = new UiPage(pageDto.getName(), pageDto.getPath());
            page.setTitle(pageDto.getTitle());
            page.setConfig(pageDto.getConfig());
            page.setActive(true);
            page = uiPageRepository.save(page);
            result.addCreatedItem(new ImportResultDto.ImportedItemDto(
                    "UI_PAGE", page.getId(), page.getName(), "CREATED"));
            addPackageItem(pkg, "UI_PAGE", page.getId(), pageDto);
        }
    }

    private void importUiMenu(PackageDto.PackageUiMenuDto menuDto,
                              ImportPackageRequest.ConflictStrategy strategy,
                              ImportResultDto result,
                              ConfigPackage pkg) {
        Optional<UiMenu> existing = uiMenuRepository.findByName(menuDto.getName());

        if (existing.isPresent()) {
            if (strategy == ImportPackageRequest.ConflictStrategy.SKIP) {
                result.addSkippedItem(new ImportResultDto.ImportedItemDto(
                        "UI_MENU", menuDto.getId(), menuDto.getName(), "SKIPPED", "Already exists"));
                return;
            } else if (strategy == ImportPackageRequest.ConflictStrategy.FAIL) {
                result.addFailedItem(new ImportResultDto.ImportedItemDto(
                        "UI_MENU", menuDto.getId(), menuDto.getName(), "FAILED", "Already exists"));
                return;
            }
            // OVERWRITE - update existing
            UiMenu menu = existing.get();
            menu.setDescription(menuDto.getDescription());
            importMenuItems(menu, menuDto.getItems());
            uiMenuRepository.save(menu);
            result.addUpdatedItem(new ImportResultDto.ImportedItemDto(
                    "UI_MENU", menu.getId(), menu.getName(), "UPDATED"));
            addPackageItem(pkg, "UI_MENU", menu.getId(), menuDto);
        } else {
            // Create new
            UiMenu menu = new UiMenu(menuDto.getName());
            menu.setDescription(menuDto.getDescription());
            menu = uiMenuRepository.save(menu);
            importMenuItems(menu, menuDto.getItems());
            uiMenuRepository.save(menu);
            result.addCreatedItem(new ImportResultDto.ImportedItemDto(
                    "UI_MENU", menu.getId(), menu.getName(), "CREATED"));
            addPackageItem(pkg, "UI_MENU", menu.getId(), menuDto);
        }
    }

    private void importMenuItems(UiMenu menu, List<PackageDto.PackageUiMenuItemDto> itemDtos) {
        // Clear existing items and add new ones
        menu.getItems().clear();
        for (PackageDto.PackageUiMenuItemDto itemDto : itemDtos) {
            UiMenuItem item = new UiMenuItem(itemDto.getLabel(), itemDto.getPath(),
                    itemDto.getDisplayOrder() != null ? itemDto.getDisplayOrder() : 0);
            item.setIcon(itemDto.getIcon());
            item.setActive(true);
            menu.addItem(item);
        }
    }

    private ImportResultDto convertPreviewToResult(ImportPreviewDto preview, boolean dryRun) {
        ImportResultDto result = new ImportResultDto(preview.getPackageName(), preview.getPackageVersion(), dryRun);
        result.setSuccess(preview.isValid());

        if (!preview.isValid()) {
            result.setErrorMessage("Package has conflicts that prevent import");
        }

        // Convert preview items to result items
        for (ImportPreviewDto.ImportItemDto item : preview.getItemsToCreate()) {
            result.addCreatedItem(new ImportResultDto.ImportedItemDto(
                    item.getItemType(), item.getItemId(), item.getItemName(), "WILL_CREATE"));
        }
        for (ImportPreviewDto.ImportItemDto item : preview.getItemsToUpdate()) {
            result.addUpdatedItem(new ImportResultDto.ImportedItemDto(
                    item.getItemType(), item.getItemId(), item.getItemName(), "WILL_UPDATE"));
        }
        for (ImportPreviewDto.ImportItemDto item : preview.getItemsToSkip()) {
            result.addSkippedItem(new ImportResultDto.ImportedItemDto(
                    item.getItemType(), item.getItemId(), item.getItemName(), "WILL_SKIP", item.getReason()));
        }
        for (ImportPreviewDto.ImportItemDto item : preview.getConflicts()) {
            result.addFailedItem(new ImportResultDto.ImportedItemDto(
                    item.getItemType(), item.getItemId(), item.getItemName(), "CONFLICT", item.getReason()));
        }

        result.updateCounts();
        return result;
    }

    /**
     * Retrieves the history of all package operations.
     * Returns a list of all packages that have been exported or imported.
     *
     * @return List of package history records
     *
     * Validates: Requirement 6.5
     */
    @Transactional(readOnly = true)
    public List<PackageDto> getPackageHistory() {
        log.info("Retrieving package history");

        List<ConfigPackage> packages = packageRepository.findAllByOrderByCreatedAtDesc();

        return packages.stream().map(pkg -> {
            PackageDto dto = new PackageDto(
                    pkg.getId(),
                    pkg.getName(),
                    pkg.getVersion(),
                    pkg.getDescription(),
                    pkg.getCreatedAt()
            );

            // Count items by type
            int collectionCount = 0;
            int oidcProviderCount = 0;
            int uiPageCount = 0;
            int uiMenuCount = 0;

            for (PackageItem item : pkg.getItems()) {
                switch (item.getItemType()) {
                    case "COLLECTION":
                        collectionCount++;
                        break;
                    case "OIDC_PROVIDER":
                        oidcProviderCount++;
                        break;
                    case "UI_PAGE":
                        uiPageCount++;
                        break;
                    case "UI_MENU":
                        uiMenuCount++;
                        break;
                }
            }

            // Set empty lists with counts for history view
            dto.setCollections(new ArrayList<>());
            dto.setOidcProviders(new ArrayList<>());
            dto.setUiPages(new ArrayList<>());
            dto.setUiMenus(new ArrayList<>());

            return dto;
        }).collect(Collectors.toList());
    }

    // ==================== Validation Helper Methods ====================

    /**
     * Validates claim path format.
     * Claim paths should be alphanumeric with dots and underscores, max 200 characters.
     *
     * @param claimPath The claim path to validate
     * @param fieldName The field name for error messages
     * @throws ValidationException if the claim path is invalid
     *
     * Validates: Requirements 9.3, 9.4
     */
    private void validateClaimPath(String claimPath, String fieldName) {
        if (claimPath == null || claimPath.isBlank()) {
            return; // null or empty is valid (will use defaults)
        }

        if (claimPath.length() > 200) {
            throw new ValidationException(fieldName,
                "Claim path must not exceed 200 characters");
        }

        // Validate claim path format (alphanumeric, dots, underscores)
        if (!claimPath.matches("^[a-zA-Z0-9_.]+$")) {
            throw new ValidationException(fieldName,
                "Claim path must contain only letters, numbers, dots, and underscores");
        }
    }

    /**
     * Validates roles mapping JSON format.
     * The roles mapping should be a valid JSON object that maps external role names to internal role names.
     *
     * @param rolesMapping The roles mapping JSON string
     * @throws ValidationException if the JSON is invalid
     *
     * Validates: Requirements 9.3, 9.4
     */
    private void validateRolesMapping(String rolesMapping) {
        if (rolesMapping == null || rolesMapping.isBlank()) {
            return; // null or empty is valid
        }

        try {
            // Attempt to parse as JSON to validate format
            objectMapper.readTree(rolesMapping);
        } catch (Exception e) {
            throw new ValidationException("rolesMapping",
                "Invalid JSON format: " + e.getMessage());
        }
    }

    /**
     * Applies default values for missing claim fields.
     * This ensures backward compatibility when importing packages that don't have claim fields.
     *
     * @param providerDto The OIDC provider DTO to apply defaults to
     *
     * Validates: Requirement 9.3
     */
    private void applyClaimDefaults(PackageDto.PackageOidcProviderDto providerDto) {
        if (providerDto.getEmailClaim() == null || providerDto.getEmailClaim().isBlank()) {
            providerDto.setEmailClaim("email");
        }
        if (providerDto.getUsernameClaim() == null || providerDto.getUsernameClaim().isBlank()) {
            providerDto.setUsernameClaim("preferred_username");
        }
        if (providerDto.getNameClaim() == null || providerDto.getNameClaim().isBlank()) {
            providerDto.setNameClaim("name");
        }
    }
}
