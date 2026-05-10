export type FormulaValue = string | number | boolean | null | undefined;

export type FormulaContext = {
  fieldValues: Record<string, unknown>;
  functions: Map<string, FormulaFunction>;
};

export type FormulaFunction = {
  name: string;
  execute(args: unknown[], context: FormulaContext): unknown;
};
