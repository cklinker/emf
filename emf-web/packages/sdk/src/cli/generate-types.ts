#!/usr/bin/env node
/**
 * CLI tool for generating TypeScript types from OpenAPI specifications
 * 
 * Usage:
 *   npx @emf/sdk generate-types --url <openapi-url> --output <output-file>
 *   npx @emf/sdk generate-types -u http://localhost:8080/openapi.json -o ./src/types/api.ts
 */

import { generateTypesWithResult } from './type-generator';
import { validateOpenAPISpec } from './openapi-parser';
import type { TypeGenerationOptions, TypeGenerationResult } from './types';

/**
 * Parse command line arguments
 */
export function parseArgs(args: string[]): TypeGenerationOptions | { error: string } {
  const options: Partial<TypeGenerationOptions> = {
    includeRequests: true,
    includeResponses: true,
  };

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    const nextArg = args[i + 1];

    switch (arg) {
      case '-u':
      case '--url':
        if (!nextArg || nextArg.startsWith('-')) {
          return { error: 'Missing value for --url option' };
        }
        options.url = nextArg;
        i++;
        break;

      case '-o':
      case '--output':
        if (!nextArg || nextArg.startsWith('-')) {
          return { error: 'Missing value for --output option' };
        }
        options.output = nextArg;
        i++;
        break;

      case '--no-requests':
        options.includeRequests = false;
        break;

      case '--no-responses':
        options.includeResponses = false;
        break;

      case '-h':
      case '--help':
        return { error: 'help' };

      default:
        if (arg.startsWith('-')) {
          return { error: `Unknown option: ${arg}` };
        }
    }
  }

  if (!options.url) {
    return { error: 'Missing required --url option' };
  }

  if (!options.output) {
    return { error: 'Missing required --output option' };
  }

  return options as TypeGenerationOptions;
}

/**
 * Print help message
 */
export function printHelp(): void {
  console.log(`
EMF Type Generator - Generate TypeScript types from OpenAPI specifications

Usage:
  generate-types --url <openapi-url> --output <output-file> [options]

Options:
  -u, --url <url>       URL to fetch OpenAPI specification from (required)
  -o, --output <file>   Output file path for generated types (required)
  --no-requests         Skip generating request types
  --no-responses        Skip generating response types
  -h, --help            Show this help message

Examples:
  generate-types -u http://localhost:8080/openapi.json -o ./src/types/api.ts
  generate-types --url https://api.example.com/v3/api-docs --output ./types.ts
`);
}

/**
 * Fetch OpenAPI spec from URL
 */
export async function fetchOpenAPISpec(url: string): Promise<unknown> {
  // Use dynamic import for fetch to support both Node.js and browser environments
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`Failed to fetch OpenAPI spec: ${response.status} ${response.statusText}`);
  }

  const contentType = response.headers.get('content-type') || '';
  
  if (contentType.includes('application/json') || contentType.includes('application/yaml') || url.endsWith('.json')) {
    return response.json();
  }

  // Try to parse as JSON anyway
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    throw new Error('Failed to parse OpenAPI spec as JSON');
  }
}

/**
 * Write content to file
 */
export async function writeFile(path: string, content: string): Promise<void> {
  // Use dynamic import for fs to support both Node.js environments
  const fs = await import('fs/promises');
  const pathModule = await import('path');

  // Ensure directory exists
  const dir = pathModule.dirname(path);
  await fs.mkdir(dir, { recursive: true });

  // Write file
  await fs.writeFile(path, content, 'utf-8');
}

/**
 * Main CLI function
 */
export async function main(args: string[]): Promise<TypeGenerationResult> {
  const parsedArgs = parseArgs(args);

  if ('error' in parsedArgs) {
    if (parsedArgs.error === 'help') {
      printHelp();
      return {
        success: true,
        typesGenerated: 0,
        outputPath: '',
      };
    }
    console.error(`Error: ${parsedArgs.error}`);
    printHelp();
    return {
      success: false,
      typesGenerated: 0,
      outputPath: '',
      errors: [parsedArgs.error],
    };
  }

  const { url, output, includeRequests, includeResponses } = parsedArgs;

  console.log(`Fetching OpenAPI spec from: ${url}`);

  try {
    // Fetch spec
    const spec = await fetchOpenAPISpec(url);

    // Validate spec
    const validation = validateOpenAPISpec(spec);
    if (!validation.valid) {
      console.error('Invalid OpenAPI specification:');
      validation.errors.forEach((err) => console.error(`  - ${err}`));
      return {
        success: false,
        typesGenerated: 0,
        outputPath: output,
        errors: validation.errors,
      };
    }

    // Generate types
    const { content, result } = generateTypesWithResult(spec, output, {
      includeRequests,
      includeResponses,
    });

    if (!result.success) {
      console.error('Failed to generate types:');
      result.errors?.forEach((err) => console.error(`  - ${err}`));
      return result;
    }

    // Write to file
    await writeFile(output, content);

    console.log(`Successfully generated ${result.typesGenerated} types to: ${output}`);
    return result;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`Error: ${message}`);
    return {
      success: false,
      typesGenerated: 0,
      outputPath: output,
      errors: [message],
    };
  }
}

/**
 * Programmatic API for type generation
 */
export async function generateTypesFromUrl(
  url: string,
  outputPath: string,
  options: { includeRequests?: boolean; includeResponses?: boolean } = {}
): Promise<TypeGenerationResult> {
  const spec = await fetchOpenAPISpec(url);
  const { content, result } = generateTypesWithResult(spec, outputPath, options);

  if (result.success) {
    await writeFile(outputPath, content);
  }

  return result;
}

/**
 * Generate types from an already-loaded spec object
 */
export function generateTypesFromSpec(
  spec: unknown,
  outputPath: string,
  options: { includeRequests?: boolean; includeResponses?: boolean } = {}
): { content: string; result: TypeGenerationResult } {
  return generateTypesWithResult(spec, outputPath, options);
}

// Run CLI if executed directly
// Check if this module is the main entry point
const isMainModule = typeof process !== 'undefined' && 
  process.argv[1] && 
  (process.argv[1].endsWith('generate-types.ts') || 
   process.argv[1].endsWith('generate-types.js') ||
   process.argv[1].includes('@emf/sdk'));

if (isMainModule) {
  main(process.argv.slice(2)).catch((error) => {
    console.error('Unexpected error:', error);
    process.exit(1);
  });
}
