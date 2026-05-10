import { evaluateAst, extractFieldRefs as astExtractFieldRefs } from './ast';
import type { FormulaAst } from './ast';
import { buildBuiltinMap } from './builtins';
import { FormulaException } from './errors';
import { FormulaParser } from './parser';
import type { FormulaFunction } from './types';

const DEFAULT_CACHE_MAX_SIZE = 1000;

export interface FormulaEvaluatorOptions {
  cacheMaxSize?: number;
  functions?: FormulaFunction[];
}

export class FormulaEvaluator {
  private readonly parser = new FormulaParser();
  private readonly functions: Map<string, FormulaFunction>;
  private readonly cache = new Map<string, FormulaAst>();
  private readonly cacheMaxSize: number;

  constructor(opts: FormulaEvaluatorOptions = {}) {
    this.cacheMaxSize = opts.cacheMaxSize ?? DEFAULT_CACHE_MAX_SIZE;
    this.functions = buildBuiltinMap(opts.functions ?? []);
  }

  registerFunction(fn: FormulaFunction): void {
    this.functions.set(fn.name.toUpperCase(), fn);
  }

  parse(expression: string): FormulaAst {
    return this.parser.parse(expression);
  }

  evaluate(expression: string, fieldValues: Record<string, unknown>): unknown {
    const ast = this.getOrParseAst(expression);
    return evaluateAst(ast, { fieldValues, functions: this.functions });
  }

  evaluateBoolean(expression: string, fieldValues: Record<string, unknown>): boolean {
    const result = this.evaluate(expression, fieldValues);
    if (typeof result === 'boolean') return result;
    throw new FormulaException(`Expression did not evaluate to Boolean: ${expression}`);
  }

  validate(expression: string): void {
    this.parser.parse(expression);
  }

  extractFieldRefs(expression: string): string[] {
    const ast = this.getOrParseAst(expression);
    return [...astExtractFieldRefs(ast)];
  }

  evict(expression: string): void {
    this.cache.delete(expression);
  }

  clearCache(): void {
    this.cache.clear();
  }

  cacheSize(): number {
    return this.cache.size;
  }

  private getOrParseAst(expression: string): FormulaAst {
    const cached = this.cache.get(expression);
    if (cached) return cached;
    const ast = this.parser.parse(expression);
    if (this.cache.size >= this.cacheMaxSize) {
      this.cache.clear();
    }
    this.cache.set(expression, ast);
    return ast;
  }
}
