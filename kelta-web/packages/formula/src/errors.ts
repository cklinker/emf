export class FormulaException extends Error {
  readonly position?: number;

  constructor(message: string, position?: number) {
    super(message);
    this.name = 'FormulaException';
    this.position = position;
  }
}
