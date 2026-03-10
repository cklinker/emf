-- Phase 2 A1: Add field type configuration columns
-- Supports type-specific config (picklist settings, auto-number format, formula expressions, etc.)

-- Type-specific configuration stored as JSONB
ALTER TABLE field ADD COLUMN field_type_config JSONB;

-- Sequence name for AUTO_NUMBER fields
ALTER TABLE field ADD COLUMN auto_number_sequence_name VARCHAR(100);

COMMENT ON COLUMN field.field_type_config IS
'Type-specific configuration. Schema varies by field type:
  PICKLIST: {"globalPicklistId": "uuid", "restricted": true, "sorted": false}
  MULTI_PICKLIST: {"globalPicklistId": "uuid", "restricted": true, "sorted": false}
  AUTO_NUMBER: {"prefix": "TICKET-", "padding": 4, "startValue": 1}
  CURRENCY: {"precision": 2, "defaultCurrencyCode": "USD"}
  FORMULA: {"expression": "Amount * Quantity", "returnType": "DOUBLE"}
  ROLLUP_SUMMARY: {"childCollection": "line_items", "aggregateFunction": "SUM", "aggregateField": "amount", "filter": {}}
  ENCRYPTED: {"algorithm": "AES-256-GCM"}
  GEOLOCATION: {"format": "DECIMAL_DEGREES"}
';
