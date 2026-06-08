package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.UniqueConstraintViolationException;
import io.kelta.runtime.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "id", "createdAt", "updatedAt", "createdBy", "updatedBy");

    private static final int MAX_ROWS = 10_000;

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final ObjectMapper objectMapper;

    public CsvImportService(QueryEngine queryEngine,
                            CollectionRegistry collectionRegistry,
                            ObjectMapper objectMapper) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.objectMapper = objectMapper;
    }

    public record RowError(int row, String message) {}

    public record ImportResult(int rowsProcessed, int rowsImported, List<RowError> errors) {}

    public ImportResult importCsv(String collectionName, InputStream csvStream) throws IOException {
        CollectionDefinition definition = collectionRegistry.get(collectionName);
        if (definition == null) {
            throw new IllegalArgumentException("Collection not found: " + collectionName);
        }

        Map<String, FieldDefinition> fieldsByName = new LinkedHashMap<>();
        if (definition.fields() != null) {
            for (FieldDefinition f : definition.fields()) {
                fieldsByName.put(f.name(), f);
            }
        }

        List<String[]> rows = parseCsv(csvStream);
        if (rows.isEmpty()) {
            return new ImportResult(0, 0, List.of());
        }

        String[] headers = rows.get(0);
        List<String> columnNames = Arrays.asList(headers);

        // Identify which columns map to writable fields
        List<String> importableColumns = columnNames.stream()
                .map(String::trim)
                .filter(name -> !name.isBlank() && !SYSTEM_FIELDS.contains(name) && fieldsByName.containsKey(name))
                .toList();

        if (importableColumns.isEmpty()) {
            return new ImportResult(0, 0,
                    List.of(new RowError(1, "No matching fields found in CSV header. " +
                            "Expected columns matching field names of collection '" + collectionName + "'")));
        }

        int dataRowCount = rows.size() - 1;
        if (dataRowCount > MAX_ROWS) {
            return new ImportResult(0, 0,
                    List.of(new RowError(0, "CSV exceeds maximum of " + MAX_ROWS + " rows. " +
                            "Split the file and import in batches.")));
        }

        List<RowError> errors = new ArrayList<>();
        int imported = 0;

        for (int i = 1; i < rows.size(); i++) {
            String[] cells = rows.get(i);
            int rowNumber = i + 1; // 1-based, row 1 is header

            // Skip completely blank rows
            if (isBlankRow(cells)) continue;

            Map<String, Object> record = new LinkedHashMap<>();
            boolean rowHasParseError = false;

            for (String colName : importableColumns) {
                int colIdx = columnNames.indexOf(colName);
                String rawValue = colIdx < cells.length ? cells[colIdx].trim() : "";
                FieldDefinition field = fieldsByName.get(colName);

                try {
                    Object coerced = coerceValue(rawValue, field);
                    if (coerced != null) {
                        record.put(colName, coerced);
                    }
                } catch (Exception e) {
                    errors.add(new RowError(rowNumber,
                            "Column '" + colName + "': " + e.getMessage()));
                    rowHasParseError = true;
                    break;
                }
            }

            if (rowHasParseError) continue;

            try {
                queryEngine.create(definition, record);
                imported++;
            } catch (ValidationException e) {
                String msg = e.getValidationResult().errors().stream()
                        .map(fe -> fe.fieldName() + ": " + fe.message())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse(e.getMessage());
                errors.add(new RowError(rowNumber, msg));
            } catch (UniqueConstraintViolationException e) {
                errors.add(new RowError(rowNumber, "Duplicate value: " + e.getMessage()));
            } catch (Exception e) {
                log.warn("Row {} import failed: {}", rowNumber, e.getMessage());
                errors.add(new RowError(rowNumber, e.getMessage()));
            }
        }

        return new ImportResult(dataRowCount, imported, errors);
    }

    // =========================================================================
    // CSV parsing (RFC 4180)
    // =========================================================================

    private List<String[]> parseCsv(InputStream stream) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) continue; // skip section headers from multi-collection export
                rows.add(parseCsvRow(line));
            }
        }
        return rows;
    }

    private String[] parseCsvRow(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(String[]::new);
    }

    private boolean isBlankRow(String[] cells) {
        for (String cell : cells) {
            if (cell != null && !cell.trim().isEmpty()) return false;
        }
        return true;
    }

    // =========================================================================
    // Type coercion
    // =========================================================================

    @SuppressWarnings("unchecked")
    private Object coerceValue(String raw, FieldDefinition field) {
        if (raw == null || raw.isEmpty()) {
            return null; // let the engine apply the default or null
        }

        FieldType type = field.type();
        return switch (type) {
            case STRING, TEXT, RICH_TEXT, PICKLIST, PHONE, EMAIL, URL, ENCRYPTED, EXTERNAL_ID -> raw;
            case INTEGER -> {
                try {
                    yield Long.parseLong(raw);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("expected integer, got '" + raw + "'");
                }
            }
            case LONG -> {
                try {
                    yield Long.parseLong(raw);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("expected integer, got '" + raw + "'");
                }
            }
            case DOUBLE, CURRENCY, PERCENT -> {
                try {
                    yield Double.parseDouble(raw);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("expected number, got '" + raw + "'");
                }
            }
            case BOOLEAN -> {
                if ("true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw)) {
                    yield Boolean.TRUE;
                } else if ("false".equalsIgnoreCase(raw) || "0".equals(raw) || "no".equalsIgnoreCase(raw)) {
                    yield Boolean.FALSE;
                } else {
                    throw new IllegalArgumentException("expected boolean (true/false/1/0), got '" + raw + "'");
                }
            }
            case DATE, DATETIME -> raw; // pass as string; storage layer parses ISO dates
            case MULTI_PICKLIST -> {
                // Exported as JSON array; also accept comma-separated plain list
                if (raw.startsWith("[")) {
                    try {
                        yield objectMapper.readValue(raw, List.class);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("expected JSON array for multi-picklist, got '" + raw + "'");
                    }
                } else {
                    yield Arrays.stream(raw.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                }
            }
            default -> raw; // URL, PHONE, EMAIL, GEOLOCATION etc — pass as string
        };
    }
}
