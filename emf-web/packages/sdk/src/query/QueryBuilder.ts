import type { ResourceClient } from '../resources/ResourceClient';
import type { ListOptions, ListResponse, SortCriteria, FilterExpression } from '../resources/types';

/**
 * Fluent query builder for constructing resource queries
 */
export class QueryBuilder<T = unknown> {
  private pageNumber?: number;
  private pageSize?: number;
  private sortCriteria: SortCriteria[] = [];
  private filterExpressions: FilterExpression[] = [];
  private selectedFields: string[] = [];

  constructor(private readonly resourceClient: ResourceClient<T>) {}

  /**
   * Set pagination parameters
   */
  paginate(page: number, size: number): QueryBuilder<T> {
    this.pageNumber = page;
    this.pageSize = size;
    return this;
  }

  /**
   * Add a sort criterion
   */
  sort(field: string, direction: 'asc' | 'desc'): QueryBuilder<T> {
    this.sortCriteria.push({ field, direction });
    return this;
  }

  /**
   * Add a filter expression
   */
  filter(
    field: string,
    operator: FilterExpression['operator'],
    value: unknown
  ): QueryBuilder<T> {
    this.filterExpressions.push({ field, operator, value });
    return this;
  }

  /**
   * Select specific fields to include in the response
   */
  fields(...fieldNames: string[]): QueryBuilder<T> {
    this.selectedFields.push(...fieldNames);
    return this;
  }

  /**
   * Build the list options from accumulated state
   */
  buildOptions(): ListOptions {
    const options: ListOptions = {};

    if (this.pageNumber !== undefined) {
      options.page = this.pageNumber;
    }

    if (this.pageSize !== undefined) {
      options.size = this.pageSize;
    }

    if (this.sortCriteria.length > 0) {
      options.sort = [...this.sortCriteria];
    }

    if (this.filterExpressions.length > 0) {
      options.filters = [...this.filterExpressions];
    }

    if (this.selectedFields.length > 0) {
      options.fields = [...this.selectedFields];
    }

    return options;
  }

  /**
   * Build query parameters string (for testing)
   */
  buildQueryParams(): string {
    const options = this.buildOptions();
    const params: string[] = [];

    if (options.page !== undefined) {
      params.push(`page=${options.page}`);
    }

    if (options.size !== undefined) {
      params.push(`size=${options.size}`);
    }

    if (options.sort) {
      options.sort.forEach((s) => {
        params.push(`sort=${s.field},${s.direction}`);
      });
    }

    if (options.filters) {
      options.filters.forEach((f) => {
        params.push(`${f.field}[${f.operator}]=${f.value}`);
      });
    }

    if (options.fields) {
      params.push(`fields=${options.fields.join(',')}`);
    }

    return params.join('&');
  }

  /**
   * Execute the query and return results
   */
  async execute(): Promise<ListResponse<T>> {
    const options = this.buildOptions();
    return this.resourceClient.list(options);
  }

  /**
   * Reset the query builder state
   */
  reset(): QueryBuilder<T> {
    this.pageNumber = undefined;
    this.pageSize = undefined;
    this.sortCriteria = [];
    this.filterExpressions = [];
    this.selectedFields = [];
    return this;
  }
}
