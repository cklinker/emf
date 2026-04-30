/**
 * Catalog of built-in formula functions supported by the backend
 * {@code MergeFieldRenderer} / {@code FormulaEvaluator}.
 *
 * Source of truth: {@code BuiltInFunctions.java}. When a function is added or
 * removed there, mirror the change here.
 */

import type { FunctionDef } from './types'

export const FUNCTIONS: readonly FunctionDef[] = [
  {
    name: 'IF',
    category: 'logical',
    args: ['condition', 'then', 'else'],
    returnType: 'any',
    description: 'Returns one value when the condition is true, another when false.',
    example: 'IF(amount > 100, "Big order", "Small order")',
  },
  {
    name: 'AND',
    category: 'logical',
    args: ['expr1', 'expr2'],
    returnType: 'boolean',
    description: 'Returns true when every argument is truthy.',
    example: 'AND(active, amount > 0)',
  },
  {
    name: 'OR',
    category: 'logical',
    args: ['expr1', 'expr2'],
    returnType: 'boolean',
    description: 'Returns true when any argument is truthy.',
    example: 'OR(status = "open", priority = "high")',
  },
  {
    name: 'NOT',
    category: 'logical',
    args: ['expr'],
    returnType: 'boolean',
    description: 'Inverts a boolean value.',
    example: 'NOT(archived)',
  },
  {
    name: 'ISBLANK',
    category: 'logical',
    args: ['value'],
    returnType: 'boolean',
    description: 'Returns true when the value is null or an empty string.',
    example: 'ISBLANK(middleName)',
  },
  {
    name: 'BLANKVALUE',
    category: 'logical',
    args: ['value', 'fallback'],
    returnType: 'any',
    description: 'Returns the fallback when the value is blank, otherwise the value.',
    example: 'BLANKVALUE(nickname, firstName)',
  },

  {
    name: 'LEN',
    category: 'text',
    args: ['text'],
    returnType: 'number',
    description: 'Returns the number of characters in the text.',
    example: 'LEN(description)',
  },
  {
    name: 'CONTAINS',
    category: 'text',
    args: ['text', 'search'],
    returnType: 'boolean',
    description: 'Returns true when the text contains the search string.',
    example: 'CONTAINS(notes, "urgent")',
  },
  {
    name: 'UPPER',
    category: 'text',
    args: ['text'],
    returnType: 'string',
    description: 'Returns the text in uppercase.',
    example: 'UPPER(firstName)',
  },
  {
    name: 'LOWER',
    category: 'text',
    args: ['text'],
    returnType: 'string',
    description: 'Returns the text in lowercase.',
    example: 'LOWER(email)',
  },
  {
    name: 'TRIM',
    category: 'text',
    args: ['text'],
    returnType: 'string',
    description: 'Removes leading and trailing whitespace.',
    example: 'TRIM(name)',
  },
  {
    name: 'TEXT',
    category: 'conversion',
    args: ['value'],
    returnType: 'string',
    description: 'Converts any value to its text representation.',
    example: 'TEXT(amount)',
  },
  {
    name: 'VALUE',
    category: 'conversion',
    args: ['text'],
    returnType: 'number',
    description: 'Converts a text value to a number.',
    example: 'VALUE("42.5")',
  },
  {
    name: 'REGEX',
    category: 'text',
    args: ['text', 'pattern'],
    returnType: 'boolean',
    description: 'Returns true when the text matches the regular expression.',
    example: 'REGEX(email, "^[^@]+@[^@]+$")',
  },

  {
    name: 'ROUND',
    category: 'math',
    args: ['number', 'digits'],
    returnType: 'number',
    description: 'Rounds to the given number of decimal places.',
    example: 'ROUND(price, 2)',
  },
  {
    name: 'ABS',
    category: 'math',
    args: ['number'],
    returnType: 'number',
    description: 'Returns the absolute value of a number.',
    example: 'ABS(balance)',
  },
  {
    name: 'MAX',
    category: 'math',
    args: ['a', 'b'],
    returnType: 'number',
    description: 'Returns the larger of two numbers.',
    example: 'MAX(min_qty, requested_qty)',
  },
  {
    name: 'MIN',
    category: 'math',
    args: ['a', 'b'],
    returnType: 'number',
    description: 'Returns the smaller of two numbers.',
    example: 'MIN(stock, requested_qty)',
  },

  {
    name: 'TODAY',
    category: 'date',
    args: [],
    returnType: 'date',
    description: "Returns today's date.",
    example: 'TODAY()',
  },
  {
    name: 'NOW',
    category: 'date',
    args: [],
    returnType: 'datetime',
    description: 'Returns the current date and time.',
    example: 'NOW()',
  },
  {
    name: 'DATEDIFF',
    category: 'date',
    args: ['date1', 'date2'],
    returnType: 'number',
    description: 'Returns the number of days between two dates (date1 - date2).',
    example: 'DATEDIFF(dueDate, TODAY())',
  },
] as const

/**
 * Builds the insertion stub for a function — e.g. `IF(${condition}, ${then}, ${else})`.
 * Callers wrap this in `{{...}}` if needed.
 */
export function buildFunctionStub(fn: FunctionDef): string {
  if (fn.args.length === 0) {
    return `${fn.name}()`
  }
  const placeholders = fn.args.map((a) => `\${${a}}`).join(', ')
  return `${fn.name}(${placeholders})`
}

export const CATEGORY_LABELS: Record<string, string> = {
  logical: 'Logical',
  text: 'Text',
  math: 'Math',
  date: 'Date & time',
  conversion: 'Conversion',
}
