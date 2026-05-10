import { describe, it, expect } from 'vitest';
import { topologicalSort, downstreamRules, type RuleNode } from './dependencyGraph';

describe('topologicalSort', () => {
  it('returns empty for empty input', () => {
    expect(topologicalSort([])).toEqual({ ok: true, order: [] });
  });

  it('orders independent rules by id (any order)', () => {
    const rules: RuleNode[] = [
      { id: 'a', target: 'fa', dependsOn: [] },
      { id: 'b', target: 'fb', dependsOn: [] },
    ];
    const r = topologicalSort(rules);
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.order.sort()).toEqual(['a', 'b']);
  });

  it('orders dependencies before dependents', () => {
    const rules: RuleNode[] = [
      { id: 'r1', target: 'sub', dependsOn: ['qty', 'price'] },
      { id: 'r2', target: 'tax', dependsOn: ['sub'] },
      { id: 'r3', target: 'total', dependsOn: ['sub', 'tax'] },
    ];
    const r = topologicalSort(rules);
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.order.indexOf('r1')).toBeLessThan(r.order.indexOf('r2'));
      expect(r.order.indexOf('r2')).toBeLessThan(r.order.indexOf('r3'));
    }
  });

  it('detects cycles and returns the cycle path', () => {
    const rules: RuleNode[] = [
      { id: 'a', target: 'fa', dependsOn: ['fb'] },
      { id: 'b', target: 'fb', dependsOn: ['fa'] },
    ];
    const r = topologicalSort(rules);
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.cycle.length).toBeGreaterThan(0);
  });

  it('ignores dependencies on non-rule fields', () => {
    const rules: RuleNode[] = [
      { id: 'r1', target: 'sub', dependsOn: ['qty', 'price', 'unknown_field'] },
    ];
    expect(topologicalSort(rules)).toEqual({ ok: true, order: ['r1'] });
  });
});

describe('downstreamRules', () => {
  const rules: RuleNode[] = [
    { id: 'r1', target: 'sub', dependsOn: ['qty', 'price'] },
    { id: 'r2', target: 'tax', dependsOn: ['sub'] },
    { id: 'r3', target: 'total', dependsOn: ['sub', 'tax'] },
    { id: 'r4', target: 'unrelated', dependsOn: ['unrelated_input'] },
  ];
  const order = ['r1', 'r2', 'r3', 'r4'];

  it('returns rules dependent on a changed field', () => {
    const out = downstreamRules('qty', rules, order);
    expect(out).toEqual(['r1', 'r2', 'r3']);
  });

  it('does not include unrelated rules', () => {
    expect(downstreamRules('qty', rules, order)).not.toContain('r4');
  });

  it('returns empty for fields with no dependents', () => {
    expect(downstreamRules('orphan', rules, order)).toEqual([]);
  });
});
