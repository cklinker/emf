/**
 * CLI module for OpenAPI type generation
 */

// Types
export type {
  OpenAPISpec,
  SchemaObject,
  ReferenceObject,
  ParsedCollection,
  ParsedField,
  TypeGenerationOptions,
  TypeGenerationResult,
} from './types';

// Parser functions
export {
  parseCollections,
  validateOpenAPISpec,
  isReferenceObject,
  resolveReference,
  getSchema,
  mapOpenAPITypeToTS,
  parseSchemaToField,
  extractCollectionName,
  getOperations,
  toDisplayName,
} from './openapi-parser';

// Generator functions
export {
  toInterfaceName,
  generateFieldType,
  generateCollectionInterface,
  generateRequestType,
  generateListResponseType,
  generateCollectionTypes,
  generateFileHeader,
  generateCommonTypes,
  generateTypes,
  generateTypesWithResult,
} from './type-generator';

// CLI functions
export {
  parseArgs,
  printHelp,
  fetchOpenAPISpec,
  writeFile,
  main,
  generateTypesFromUrl,
  generateTypesFromSpec,
} from './generate-types';
