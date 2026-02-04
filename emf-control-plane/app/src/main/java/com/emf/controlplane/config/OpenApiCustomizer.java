package com.emf.controlplane.config;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Customizes the OpenAPI specification to include dynamically generated paths
 * for runtime collections.
 * 
 * <p>This component implements the springdoc OpenApiCustomiser interface to add
 * dynamic API paths for each active collection in the system. For each collection,
 * it generates CRUD operation paths and corresponding schemas.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>9.3: Include all static endpoints in the OpenAPI specification</li>
 *   <li>9.4: Include dynamically generated endpoints for runtime collections</li>
 * </ul>
 */
@Component
public class OpenApiCustomizer implements GlobalOpenApiCustomizer {

    private static final Logger log = LoggerFactory.getLogger(OpenApiCustomizer.class);

    private static final String BEARER_JWT = "bearer-jwt";
    private static final String APPLICATION_JSON = "application/json";

    private final CollectionRepository collectionRepository;
    private final FieldRepository fieldRepository;

    public OpenApiCustomizer(CollectionRepository collectionRepository, 
                            FieldRepository fieldRepository) {
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
    }

    /**
     * Customizes the OpenAPI specification by adding dynamic paths for runtime collections.
     * 
     * <p>For each active collection, this method generates:
     * <ul>
     *   <li>GET /api/{collectionName} - List all items</li>
     *   <li>POST /api/{collectionName} - Create a new item</li>
     *   <li>GET /api/{collectionName}/{id} - Get item by ID</li>
     *   <li>PUT /api/{collectionName}/{id} - Update item by ID</li>
     *   <li>DELETE /api/{collectionName}/{id} - Delete item by ID</li>
     * </ul>
     * 
     * @param openApi The OpenAPI specification to customize
     * 
     * Validates: Requirements 9.3, 9.4
     */
    @Override
    public void customise(OpenAPI openApi) {
        log.info("Customizing OpenAPI specification with dynamic collection paths");

        try {
            // Get all active collections
            List<Collection> collections = collectionRepository.findByActiveTrue();
            
            if (collections.isEmpty()) {
                log.debug("No active collections found - skipping dynamic path generation");
                return;
            }

            log.info("Generating OpenAPI paths for {} active collections", collections.size());

            // Ensure components and schemas exist
            if (openApi.getComponents() == null) {
                openApi.setComponents(new io.swagger.v3.oas.models.Components());
            }
            if (openApi.getComponents().getSchemas() == null) {
                openApi.getComponents().setSchemas(new LinkedHashMap<>());
            }
            if (openApi.getPaths() == null) {
                openApi.setPaths(new io.swagger.v3.oas.models.Paths());
            }

            // Generate paths and schemas for each collection
            for (Collection collection : collections) {
                addCollectionPaths(openApi, collection);
            }

            log.info("Successfully added dynamic paths for {} collections", collections.size());
        } catch (Exception e) {
            log.error("Failed to customize OpenAPI specification: {}", e.getMessage(), e);
            // Don't throw - allow the API to still work with static paths
        }
    }

    /**
     * Adds CRUD operation paths for a specific collection.
     * 
     * @param openApi The OpenAPI specification
     * @param collection The collection to generate paths for
     * 
     * Validates: Requirement 9.4
     */
    private void addCollectionPaths(OpenAPI openApi, Collection collection) {
        String collectionName = collection.getName().toLowerCase().replace(" ", "-");
        String basePath = "/api/" + collectionName;
        String itemPath = basePath + "/{id}";
        String tag = "Runtime: " + collection.getName();

        log.debug("Adding paths for collection: {} at {}", collection.getName(), basePath);

        // Generate schemas for this collection
        String dtoSchemaName = generateSchemaName(collection.getName()) + "Dto";
        String createRequestSchemaName = "Create" + generateSchemaName(collection.getName()) + "Request";
        String updateRequestSchemaName = "Update" + generateSchemaName(collection.getName()) + "Request";

        // Get active fields for this collection
        List<Field> fields = fieldRepository.findByCollectionIdAndActiveTrue(collection.getId());

        // Generate and add schemas
        Schema<?> dtoSchema = generateCollectionDtoSchema(collection, fields);
        Schema<?> createRequestSchema = generateCreateRequestSchema(collection, fields);
        Schema<?> updateRequestSchema = generateUpdateRequestSchema(collection, fields);
        Schema<?> listResponseSchema = generateListResponseSchema(dtoSchemaName);

        openApi.getComponents().getSchemas().put(dtoSchemaName, dtoSchema);
        openApi.getComponents().getSchemas().put(createRequestSchemaName, createRequestSchema);
        openApi.getComponents().getSchemas().put(updateRequestSchemaName, updateRequestSchema);
        openApi.getComponents().getSchemas().put(dtoSchemaName + "ListResponse", listResponseSchema);

        // Create path items for collection endpoints
        PathItem collectionPathItem = createCollectionPathItem(collection, tag, dtoSchemaName, createRequestSchemaName);
        PathItem itemPathItem = createItemPathItem(collection, tag, dtoSchemaName, updateRequestSchemaName);

        // Add paths to OpenAPI spec
        openApi.getPaths().addPathItem(basePath, collectionPathItem);
        openApi.getPaths().addPathItem(itemPath, itemPathItem);
    }

    /**
     * Creates a PathItem for collection-level operations (list, create).
     */
    private PathItem createCollectionPathItem(Collection collection, String tag, 
                                              String dtoSchemaName, String createRequestSchemaName) {
        PathItem pathItem = new PathItem();

        // GET - List all items
        Operation listOperation = new Operation()
                .summary("List " + collection.getName() + " items")
                .description("Returns a paginated list of " + collection.getName() + " items")
                .addTagsItem(tag)
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(BEARER_JWT))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Successfully retrieved items")
                                .content(new Content().addMediaType(APPLICATION_JSON,
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + dtoSchemaName + "ListResponse")))))
                        .addApiResponse("401", new ApiResponse().description("Unauthorized - invalid or missing JWT token"))
                        .addApiResponse("500", new ApiResponse().description("Internal server error")));

        // Add pagination parameters
        listOperation.addParametersItem(new Parameter()
                .name("page")
                .in("query")
                .description("Page number (0-based)")
                .schema(new IntegerSchema()._default(0)));
        listOperation.addParametersItem(new Parameter()
                .name("size")
                .in("query")
                .description("Page size")
                .schema(new IntegerSchema()._default(20)));
        listOperation.addParametersItem(new Parameter()
                .name("sort")
                .in("query")
                .description("Sort criteria (e.g., 'name,asc')")
                .schema(new StringSchema()));

        pathItem.setGet(listOperation);

        // POST - Create new item
        Operation createOperation = new Operation()
                .summary("Create " + collection.getName() + " item")
                .description("Creates a new " + collection.getName() + " item")
                .addTagsItem(tag)
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(BEARER_JWT))
                .requestBody(new RequestBody()
                        .description("Item to create")
                        .required(true)
                        .content(new Content().addMediaType(APPLICATION_JSON,
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + createRequestSchemaName)))))
                .responses(new ApiResponses()
                        .addApiResponse("201", new ApiResponse()
                                .description("Item created successfully")
                                .content(new Content().addMediaType(APPLICATION_JSON,
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + dtoSchemaName)))))
                        .addApiResponse("400", new ApiResponse().description("Invalid request - validation errors"))
                        .addApiResponse("401", new ApiResponse().description("Unauthorized - invalid or missing JWT token"))
                        .addApiResponse("403", new ApiResponse().description("Forbidden - insufficient permissions"))
                        .addApiResponse("500", new ApiResponse().description("Internal server error")));

        pathItem.setPost(createOperation);

        return pathItem;
    }

    /**
     * Creates a PathItem for item-level operations (get, update, delete).
     */
    private PathItem createItemPathItem(Collection collection, String tag,
                                        String dtoSchemaName, String updateRequestSchemaName) {
        PathItem pathItem = new PathItem();

        // Common path parameter
        Parameter idParameter = new PathParameter()
                .name("id")
                .description("Item ID")
                .required(true)
                .schema(new StringSchema());

        // GET - Get item by ID
        Operation getOperation = new Operation()
                .summary("Get " + collection.getName() + " item")
                .description("Retrieves a " + collection.getName() + " item by ID")
                .addTagsItem(tag)
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(BEARER_JWT))
                .addParametersItem(idParameter)
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Item found")
                                .content(new Content().addMediaType(APPLICATION_JSON,
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + dtoSchemaName)))))
                        .addApiResponse("401", new ApiResponse().description("Unauthorized - invalid or missing JWT token"))
                        .addApiResponse("404", new ApiResponse().description("Item not found"))
                        .addApiResponse("500", new ApiResponse().description("Internal server error")));

        pathItem.setGet(getOperation);

        // PUT - Update item
        Operation updateOperation = new Operation()
                .summary("Update " + collection.getName() + " item")
                .description("Updates an existing " + collection.getName() + " item")
                .addTagsItem(tag)
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(BEARER_JWT))
                .addParametersItem(idParameter)
                .requestBody(new RequestBody()
                        .description("Updated item data")
                        .required(true)
                        .content(new Content().addMediaType(APPLICATION_JSON,
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + updateRequestSchemaName)))))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Item updated successfully")
                                .content(new Content().addMediaType(APPLICATION_JSON,
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + dtoSchemaName)))))
                        .addApiResponse("400", new ApiResponse().description("Invalid request - validation errors"))
                        .addApiResponse("401", new ApiResponse().description("Unauthorized - invalid or missing JWT token"))
                        .addApiResponse("403", new ApiResponse().description("Forbidden - insufficient permissions"))
                        .addApiResponse("404", new ApiResponse().description("Item not found"))
                        .addApiResponse("500", new ApiResponse().description("Internal server error")));

        pathItem.setPut(updateOperation);

        // DELETE - Delete item
        Operation deleteOperation = new Operation()
                .summary("Delete " + collection.getName() + " item")
                .description("Deletes a " + collection.getName() + " item")
                .addTagsItem(tag)
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(BEARER_JWT))
                .addParametersItem(idParameter)
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Item deleted successfully"))
                        .addApiResponse("401", new ApiResponse().description("Unauthorized - invalid or missing JWT token"))
                        .addApiResponse("403", new ApiResponse().description("Forbidden - insufficient permissions"))
                        .addApiResponse("404", new ApiResponse().description("Item not found"))
                        .addApiResponse("500", new ApiResponse().description("Internal server error")));

        pathItem.setDelete(deleteOperation);

        return pathItem;
    }

    /**
     * Generates a DTO schema for a collection based on its fields.
     */
    @SuppressWarnings("rawtypes")
    private Schema<?> generateCollectionDtoSchema(Collection collection, List<Field> fields) {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription(collection.getDescription() != null ? 
                collection.getDescription() : "DTO for " + collection.getName());

        // Add standard fields
        schema.addProperty("id", new StringSchema().description("Unique identifier"));
        schema.addProperty("createdAt", new StringSchema()
                .format("date-time")
                .description("Creation timestamp"));
        schema.addProperty("updatedAt", new StringSchema()
                .format("date-time")
                .description("Last update timestamp"));

        List<String> requiredFields = new ArrayList<>();
        requiredFields.add("id");

        // Add collection-specific fields
        for (Field field : fields) {
            Schema fieldSchema = mapFieldTypeToSchema(field);
            schema.addProperty(field.getName(), fieldSchema);
            
            if (field.isRequired()) {
                requiredFields.add(field.getName());
            }
        }

        schema.setRequired(requiredFields);
        return schema;
    }

    /**
     * Generates a create request schema for a collection.
     */
    @SuppressWarnings("rawtypes")
    private Schema<?> generateCreateRequestSchema(Collection collection, List<Field> fields) {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("Request body for creating a new " + collection.getName() + " item");

        List<String> requiredFields = new ArrayList<>();

        // Add collection-specific fields (excluding id and timestamps)
        for (Field field : fields) {
            Schema fieldSchema = mapFieldTypeToSchema(field);
            schema.addProperty(field.getName(), fieldSchema);
            
            if (field.isRequired()) {
                requiredFields.add(field.getName());
            }
        }

        if (!requiredFields.isEmpty()) {
            schema.setRequired(requiredFields);
        }
        return schema;
    }

    /**
     * Generates an update request schema for a collection.
     */
    @SuppressWarnings("rawtypes")
    private Schema<?> generateUpdateRequestSchema(Collection collection, List<Field> fields) {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("Request body for updating a " + collection.getName() + " item");

        // All fields are optional in update requests
        for (Field field : fields) {
            Schema fieldSchema = mapFieldTypeToSchema(field);
            schema.addProperty(field.getName(), fieldSchema);
        }

        return schema;
    }

    /**
     * Generates a list response schema with pagination.
     */
    private Schema<?> generateListResponseSchema(String itemSchemaName) {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("Paginated list response");

        // Content array
        ArraySchema contentSchema = new ArraySchema();
        contentSchema.setItems(new Schema<>().$ref("#/components/schemas/" + itemSchemaName));
        schema.addProperty("content", contentSchema);

        // Pagination metadata
        schema.addProperty("totalElements", new IntegerSchema()
                .format("int64")
                .description("Total number of elements"));
        schema.addProperty("totalPages", new IntegerSchema()
                .description("Total number of pages"));
        schema.addProperty("size", new IntegerSchema()
                .description("Page size"));
        schema.addProperty("number", new IntegerSchema()
                .description("Current page number (0-based)"));
        schema.addProperty("first", new BooleanSchema()
                .description("Whether this is the first page"));
        schema.addProperty("last", new BooleanSchema()
                .description("Whether this is the last page"));
        schema.addProperty("empty", new BooleanSchema()
                .description("Whether the page is empty"));

        return schema;
    }

    /**
     * Maps a field type to an OpenAPI schema type.
     */
    @SuppressWarnings("rawtypes")
    private Schema mapFieldTypeToSchema(Field field) {
        Schema schema;
        String type = field.getType().toLowerCase();

        switch (type) {
            case "string":
            case "text":
                schema = new StringSchema();
                break;
            case "integer":
            case "int":
                schema = new IntegerSchema();
                break;
            case "long":
                schema = new IntegerSchema().format("int64");
                break;
            case "number":
            case "decimal":
            case "double":
            case "float":
                schema = new NumberSchema();
                break;
            case "boolean":
            case "bool":
                schema = new BooleanSchema();
                break;
            case "date":
                schema = new StringSchema().format("date");
                break;
            case "datetime":
            case "timestamp":
                schema = new StringSchema().format("date-time");
                break;
            case "uuid":
                schema = new StringSchema().format("uuid");
                break;
            case "email":
                schema = new StringSchema().format("email");
                break;
            case "uri":
            case "url":
                schema = new StringSchema().format("uri");
                break;
            case "array":
                schema = new ArraySchema().items(new StringSchema());
                break;
            case "object":
            case "json":
                schema = new ObjectSchema();
                break;
            case "reference":
                schema = new StringSchema().description("Reference to another entity");
                break;
            default:
                schema = new StringSchema();
                log.debug("Unknown field type '{}' for field '{}', defaulting to string", 
                        type, field.getName());
        }

        // Add description if available
        if (field.getDescription() != null && !field.getDescription().isEmpty()) {
            schema.setDescription(field.getDescription());
        }

        return schema;
    }

    /**
     * Generates a PascalCase schema name from a collection name.
     */
    private String generateSchemaName(String collectionName) {
        if (collectionName == null || collectionName.isEmpty()) {
            return "Unknown";
        }

        // Split by spaces, hyphens, or underscores and capitalize each word
        String[] words = collectionName.split("[\\s\\-_]+");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }
}
