import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { FormulaEvaluator } from './FormulaEvaluator';
import { FormulaException } from './errors';

interface ParityCase {
  name: string;
  expression: string;
  context: Record<string, unknown>;
  expected?: unknown;
  expectedType?: 'number' | 'string' | 'boolean';
  throws?: boolean;
}

interface ParityFixtures {
  description: string;
  cases: ParityCase[];
}

const FIXTURE_PATH = resolve(
  __dirname,
  '../../../../kelta-platform/runtime/runtime-core/src/test/resources/formula-parity-fixtures.json'
);

const fixtures: ParityFixtures = JSON.parse(readFileSync(FIXTURE_PATH, 'utf-8'));

describe('FormulaEvaluator parity (shared fixture)', () => {
  const evaluator = new FormulaEvaluator();

  for (const c of fixtures.cases) {
    it(c.name, () => {
      if (c.throws) {
        expect(() => evaluator.evaluate(c.expression, c.context)).toThrow(FormulaException);
        return;
      }
      const result = evaluator.evaluate(c.expression, c.context);
      if (typeof c.expected === 'number' && typeof result === 'number') {
        expect(result).toBeCloseTo(c.expected, 10);
      } else {
        expect(result).toEqual(c.expected);
      }
      if (c.expectedType) {
        expect(typeof result).toBe(c.expectedType);
      }
    });
  }
});
