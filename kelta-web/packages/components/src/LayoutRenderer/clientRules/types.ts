import type { LayoutRule, LayoutRuleEvent } from '@kelta/sdk';

export type { LayoutRule, LayoutRuleEvent };

export interface FormBinding {
  getValue(field: string): unknown;
  getValues(): Record<string, unknown>;
  setValue(field: string, value: unknown): void;
  setError(field: string, message: string): void;
  clearError(field: string): void;
}

export interface RuleViolation {
  ruleId: string;
  ruleName: string;
  field?: string;
  message: string;
  severity: 'error' | 'warning';
}

export interface BeforeSaveResult {
  blocked: boolean;
  violations: RuleViolation[];
}

export interface RuleEngineDiagnostic {
  ruleId: string;
  ruleName: string;
  level: 'error' | 'warning';
  message: string;
}
