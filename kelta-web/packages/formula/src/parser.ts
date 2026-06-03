import type { FormulaAst, BinaryOperator, UnaryOperator } from './ast';
import { FormulaException } from './errors';

export class FormulaParser {
  private input = '';
  private pos = 0;

  parse(expression: string): FormulaAst {
    if (expression == null || expression.trim().length === 0) {
      throw new FormulaException('Formula expression cannot be empty');
    }
    this.input = expression.trim();
    this.pos = 0;
    const result = this.parseOr();
    this.skipWhitespace();
    if (this.pos < this.input.length) {
      throw new FormulaException(
        `Unexpected character at position ${this.pos}: '${this.input.charAt(this.pos)}'`,
        this.pos
      );
    }
    return result;
  }

  private parseOr(): FormulaAst {
    let left = this.parseAnd();
    this.skipWhitespace();
    while (this.pos < this.input.length && (this.match('||') || this.matchKeyword('OR'))) {
      const right = this.parseAnd();
      left = { kind: 'binaryOp', operator: '||', left, right };
      this.skipWhitespace();
    }
    return left;
  }

  private parseAnd(): FormulaAst {
    let left = this.parseComparison();
    this.skipWhitespace();
    while (this.pos < this.input.length && (this.match('&&') || this.matchKeyword('AND'))) {
      const right = this.parseComparison();
      left = { kind: 'binaryOp', operator: '&&', left, right };
      this.skipWhitespace();
    }
    return left;
  }

  private parseComparison(): FormulaAst {
    const left = this.parseAddSub();
    this.skipWhitespace();
    if (this.pos < this.input.length) {
      const ops: BinaryOperator[] = ['>=', '<=', '!=', '<>', '==', '>', '<', '='];
      for (const op of ops) {
        if (this.match(op)) {
          const right = this.parseAddSub();
          return { kind: 'binaryOp', operator: op, left, right };
        }
      }
    }
    return left;
  }

  private parseAddSub(): FormulaAst {
    let left = this.parseMulDiv();
    this.skipWhitespace();
    while (this.pos < this.input.length) {
      if (this.match('+')) {
        left = { kind: 'binaryOp', operator: '+', left, right: this.parseMulDiv() };
      } else if (this.matchChar('-')) {
        left = { kind: 'binaryOp', operator: '-', left, right: this.parseMulDiv() };
      } else {
        break;
      }
      this.skipWhitespace();
    }
    return left;
  }

  private parseMulDiv(): FormulaAst {
    let left = this.parseUnary();
    this.skipWhitespace();
    while (this.pos < this.input.length) {
      if (this.match('*')) {
        left = { kind: 'binaryOp', operator: '*', left, right: this.parseUnary() };
      } else if (this.match('/')) {
        left = { kind: 'binaryOp', operator: '/', left, right: this.parseUnary() };
      } else {
        break;
      }
      this.skipWhitespace();
    }
    return left;
  }

  private parseUnary(): FormulaAst {
    this.skipWhitespace();
    if (this.pos < this.input.length) {
      const c = this.input.charAt(this.pos);
      if (c === '-' || c === '!') {
        this.pos++;
        const operator: UnaryOperator = c;
        return { kind: 'unaryOp', operator, operand: this.parsePrimary() };
      }
      if (this.matchKeyword('NOT')) {
        return { kind: 'unaryOp', operator: '!', operand: this.parsePrimary() };
      }
    }
    return this.parsePrimary();
  }

  private parsePrimary(): FormulaAst {
    this.skipWhitespace();
    if (this.pos >= this.input.length) {
      throw new FormulaException('Unexpected end of expression', this.pos);
    }

    const c = this.input.charAt(this.pos);

    if (c === '(') {
      this.pos++;
      const expr = this.parseOr();
      this.skipWhitespace();
      this.expect(')');
      return expr;
    }

    if (c === "'" || c === '"') {
      return this.parseStringLiteral(c);
    }

    if (
      this.isDigit(c) ||
      (c === '.' &&
        this.pos + 1 < this.input.length &&
        this.isDigit(this.input.charAt(this.pos + 1)))
    ) {
      return this.parseNumberLiteral();
    }

    if (this.isLetter(c) || c === '_') {
      return this.parseIdentifierOrFunction();
    }

    throw new FormulaException(`Unexpected character at position ${this.pos}: '${c}'`, this.pos);
  }

  private parseStringLiteral(quote: string): FormulaAst {
    this.pos++;
    let s = '';
    while (this.pos < this.input.length && this.input.charAt(this.pos) !== quote) {
      if (this.input.charAt(this.pos) === '\\' && this.pos + 1 < this.input.length) {
        this.pos++;
      }
      s += this.input.charAt(this.pos);
      this.pos++;
    }
    if (this.pos >= this.input.length) {
      throw new FormulaException('Unterminated string literal', this.pos);
    }
    this.pos++;
    return { kind: 'literal', value: s };
  }

  private parseNumberLiteral(): FormulaAst {
    const start = this.pos;
    while (
      this.pos < this.input.length &&
      (this.isDigit(this.input.charAt(this.pos)) || this.input.charAt(this.pos) === '.')
    ) {
      this.pos++;
    }
    const numStr = this.input.substring(start, this.pos);
    const n = Number(numStr);
    if (Number.isNaN(n)) {
      throw new FormulaException(`Invalid number: ${numStr}`, start);
    }
    return { kind: 'literal', value: n };
  }

  private parseIdentifierOrFunction(): FormulaAst {
    const start = this.pos;
    while (
      this.pos < this.input.length &&
      (this.isLetterOrDigit(this.input.charAt(this.pos)) || this.input.charAt(this.pos) === '_')
    ) {
      this.pos++;
    }
    const name = this.input.substring(start, this.pos);
    this.skipWhitespace();

    const lower = name.toLowerCase();
    if (lower === 'true') return { kind: 'literal', value: true };
    if (lower === 'false') return { kind: 'literal', value: false };
    if (lower === 'null') return { kind: 'literal', value: null };

    if (this.pos < this.input.length && this.input.charAt(this.pos) === '(') {
      this.pos++;
      const args: FormulaAst[] = [];
      this.skipWhitespace();
      if (this.pos < this.input.length && this.input.charAt(this.pos) !== ')') {
        args.push(this.parseOr());
        this.skipWhitespace();
        while (this.pos < this.input.length && this.input.charAt(this.pos) === ',') {
          this.pos++;
          args.push(this.parseOr());
          this.skipWhitespace();
        }
      }
      this.expect(')');
      return { kind: 'functionCall', functionName: name, arguments: args };
    }

    return { kind: 'fieldRef', fieldName: name };
  }

  private match(s: string): boolean {
    this.skipWhitespace();
    if (
      this.pos + s.length <= this.input.length &&
      this.input.substring(this.pos, this.pos + s.length) === s
    ) {
      this.pos += s.length;
      return true;
    }
    return false;
  }

  private matchKeyword(keyword: string): boolean {
    this.skipWhitespace();
    const len = keyword.length;
    if (this.pos + len > this.input.length) return false;
    if (this.input.substring(this.pos, this.pos + len).toUpperCase() !== keyword) return false;
    if (this.pos + len < this.input.length) {
      const next = this.input.charAt(this.pos + len);
      if (this.isLetterOrDigit(next) || next === '_') return false;
    }
    this.pos += len;
    return true;
  }

  private matchChar(c: string): boolean {
    this.skipWhitespace();
    if (this.pos < this.input.length && this.input.charAt(this.pos) === c) {
      this.pos++;
      return true;
    }
    return false;
  }

  private expect(c: string): void {
    this.skipWhitespace();
    if (this.pos >= this.input.length || this.input.charAt(this.pos) !== c) {
      throw new FormulaException(`Expected '${c}' at position ${this.pos}`, this.pos);
    }
    this.pos++;
  }

  private skipWhitespace(): void {
    while (this.pos < this.input.length && /\s/.test(this.input.charAt(this.pos))) {
      this.pos++;
    }
  }

  private isDigit(c: string): boolean {
    return c >= '0' && c <= '9';
  }
  private isLetter(c: string): boolean {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }
  private isLetterOrDigit(c: string): boolean {
    return this.isLetter(c) || this.isDigit(c);
  }
}
