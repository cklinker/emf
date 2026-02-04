import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FilterBuilder } from './FilterBuilder';
import type { FieldDefinition, FilterExpression } from '@emf/sdk';

// Mock field definitions
const mockFields: FieldDefinition[] = [
  { name: 'name', type: 'string', displayName: 'Full Name' },
  { name: 'email', type: 'string', displayName: 'Email Address' },
  { name: 'age', type: 'number', displayName: 'Age' },
  { name: 'isActive', type: 'boolean', displayName: 'Active Status' },
  { name: 'createdAt', type: 'date', displayName: 'Created Date' },
  { name: 'updatedAt', type: 'datetime', displayName: 'Updated At' },
];

describe('FilterBuilder', () => {
  describe('Field Display (Requirement 14.1)', () => {
    it('should display all available fields in the field selector', () => {
      const onChange = vi.fn();
      // Start with one filter already added
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      // Check that all fields are available in the dropdown
      const fieldSelect = screen.getByLabelText(/filter field 1/i);
      expect(fieldSelect).toBeInTheDocument();

      // Verify all field options are present by checking the select element
      const options = fieldSelect.querySelectorAll('option');
      expect(options).toHaveLength(mockFields.length);
      
      mockFields.forEach((field, index) => {
        expect(options[index]).toHaveTextContent(field.displayName || field.name);
      });
    });

    it('should display field display names when available', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const fieldSelect = screen.getByLabelText(/filter field 1/i);
      const options = fieldSelect.querySelectorAll('option');
      
      expect(options[0]).toHaveTextContent('Full Name');
      expect(options[1]).toHaveTextContent('Email Address');
    });

    it('should show empty state when no filters are applied', () => {
      const onChange = vi.fn();

      render(
        <FilterBuilder
          fields={mockFields}
          value={[]}
          onChange={onChange}
        />
      );

      expect(screen.getByText(/no filters applied/i)).toBeInTheDocument();
    });
  });

  describe('Add/Remove Filter UI (Requirement 14.2, 14.4)', () => {
    it('should add a new filter when Add Filter button is clicked', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();

      render(
        <FilterBuilder
          fields={mockFields}
          value={[]}
          onChange={onChange}
        />
      );

      await user.click(screen.getByRole('button', { name: /add/i }));

      expect(onChange).toHaveBeenCalledWith([
        expect.objectContaining({
          field: 'name',
          operator: 'eq',
          value: '',
        }),
      ]);
    });

    it('should remove a filter when remove button is clicked', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: 'John' },
        { field: 'age', operator: 'gt', value: 25 },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      // Remove the first filter
      const removeButtons = screen.getAllByRole('button', { name: /remove filter/i });
      await user.click(removeButtons[0]);

      expect(onChange).toHaveBeenCalledWith([
        { field: 'age', operator: 'gt', value: 25 },
      ]);
    });

    it('should disable Add Filter button when max filters reached', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: 'John' },
        { field: 'age', operator: 'gt', value: 25 },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
          maxFilters={2}
        />
      );

      expect(screen.getByRole('button', { name: /add/i })).toBeDisabled();
    });

    it('should disable Add Filter button when no fields available', () => {
      const onChange = vi.fn();

      render(
        <FilterBuilder
          fields={[]}
          value={[]}
          onChange={onChange}
        />
      );

      expect(screen.getByRole('button', { name: /add/i })).toBeDisabled();
    });

    it('should show filter count when filters exist', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: 'John' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
          maxFilters={5}
        />
      );

      expect(screen.getByText('(1/5)')).toBeInTheDocument();
    });
  });

  describe('Operators by Field Type (Requirement 14.3, 14.6, 14.7)', () => {
    it('should show string operators for string fields', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const operatorSelect = screen.getByLabelText(/filter operator 1/i);
      
      // String operators should be available
      expect(screen.getByRole('option', { name: 'Equals' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Contains' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Starts with' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Ends with' })).toBeInTheDocument();
    });

    it('should show number operators for number fields', async () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'age', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      // Number operators should be available
      expect(screen.getByRole('option', { name: 'Equals' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Greater than' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Less than' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Greater than or equal' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Less than or equal' })).toBeInTheDocument();
    });

    it('should show boolean operators for boolean fields', async () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'isActive', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      // Boolean should only have equals
      const operatorSelect = screen.getByLabelText(/filter operator 1/i);
      const options = operatorSelect.querySelectorAll('option');
      expect(options).toHaveLength(1);
      expect(options[0]).toHaveTextContent('Equals');
    });

    it('should show date operators for date fields', async () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'createdAt', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      // Date operators should be available
      expect(screen.getByRole('option', { name: 'Equals' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'After' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Before' })).toBeInTheDocument();
    });

    it('should reset operator when field type changes', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'contains', value: 'test' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      // Change field from string to number
      const fieldSelect = screen.getByLabelText(/filter field 1/i);
      await user.selectOptions(fieldSelect, 'age');

      // Should reset operator to first available for number type
      expect(onChange).toHaveBeenCalledWith([
        expect.objectContaining({
          field: 'age',
          operator: 'eq', // Reset to first number operator
          value: '', // Value should also be reset
        }),
      ]);
    });
  });

  describe('Type-Appropriate Value Inputs (Requirement 14.5)', () => {
    it('should render text input for string fields', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const valueInput = screen.getByLabelText(/filter value for name/i);
      expect(valueInput).toHaveAttribute('type', 'text');
    });

    it('should render number input for number fields', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'age', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const valueInput = screen.getByLabelText(/filter value for age/i);
      expect(valueInput).toHaveAttribute('type', 'number');
    });

    it('should render select input for boolean fields', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'isActive', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const valueInput = screen.getByLabelText(/filter value for isActive/i);
      expect(valueInput.tagName).toBe('SELECT');
      expect(screen.getByRole('option', { name: 'Yes' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'No' })).toBeInTheDocument();
    });

    it('should render date input for date fields', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'createdAt', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const valueInput = screen.getByLabelText(/filter value for createdAt/i);
      expect(valueInput).toHaveAttribute('type', 'date');
    });

    it('should render datetime-local input for datetime fields', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'updatedAt', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const valueInput = screen.getByLabelText(/filter value for updatedAt/i);
      expect(valueInput).toHaveAttribute('type', 'datetime-local');
    });
  });

  describe('onChange Callback (Requirement 14.5)', () => {
    it('should call onChange when filter value changes', async () => {
      const user = userEvent.setup();
      let currentFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: '' },
      ];
      const onChange = vi.fn((newFilters) => {
        currentFilters = newFilters;
      });

      const { rerender } = render(
        <FilterBuilder
          fields={mockFields}
          value={currentFilters}
          onChange={onChange}
        />
      );

      const valueInput = screen.getByLabelText(/filter value for name/i);
      
      // Type one character at a time, rerendering with updated value
      await user.type(valueInput, 'J');
      rerender(
        <FilterBuilder
          fields={mockFields}
          value={currentFilters}
          onChange={onChange}
        />
      );

      expect(onChange).toHaveBeenCalled();
      expect(currentFilters[0].value).toBe('J');
    });

    it('should call onChange when operator changes', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: 'test' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const operatorSelect = screen.getByLabelText(/filter operator 1/i);
      await user.selectOptions(operatorSelect, 'contains');

      expect(onChange).toHaveBeenCalledWith([
        expect.objectContaining({
          field: 'name',
          operator: 'contains',
          value: 'test',
        }),
      ]);
    });

    it('should call onChange when field changes', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: 'test' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const fieldSelect = screen.getByLabelText(/filter field 1/i);
      await user.selectOptions(fieldSelect, 'email');

      expect(onChange).toHaveBeenCalledWith([
        expect.objectContaining({
          field: 'email',
        }),
      ]);
    });

    it('should handle number value conversion correctly', async () => {
      const user = userEvent.setup();
      let currentFilters: FilterExpression[] = [
        { field: 'age', operator: 'gt', value: '' },
      ];
      const onChange = vi.fn((newFilters) => {
        currentFilters = newFilters;
      });

      const { rerender } = render(
        <FilterBuilder
          fields={mockFields}
          value={currentFilters}
          onChange={onChange}
        />
      );

      const valueInput = screen.getByLabelText(/filter value for age/i);
      
      // Clear and type the number
      await user.clear(valueInput);
      await user.type(valueInput, '2');
      rerender(
        <FilterBuilder
          fields={mockFields}
          value={currentFilters}
          onChange={onChange}
        />
      );
      await user.type(valueInput, '5');

      // The last call should have a number value
      const lastCall = onChange.mock.calls[onChange.mock.calls.length - 1];
      expect(typeof lastCall[0][0].value).toBe('number');
    });

    it('should handle boolean value conversion correctly', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'isActive', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const valueInput = screen.getByLabelText(/filter value for isActive/i);
      await user.selectOptions(valueInput, 'true');

      expect(onChange).toHaveBeenCalledWith([
        expect.objectContaining({
          field: 'isActive',
          operator: 'eq',
          value: true,
        }),
      ]);
    });
  });

  describe('Multiple Filters', () => {
    it('should render multiple filters correctly', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'contains', value: 'John' },
        { field: 'age', operator: 'gt', value: 25 },
        { field: 'isActive', operator: 'eq', value: true },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      const filterItems = screen.getAllByRole('listitem').filter(
        item => item.getAttribute('data-filter-index') !== null
      );
      expect(filterItems).toHaveLength(3);
    });

    it('should update correct filter when multiple exist', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: 'John' },
        { field: 'age', operator: 'gt', value: 25 },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      // Update the second filter's operator
      const operatorSelects = screen.getAllByLabelText(/filter operator/i);
      await user.selectOptions(operatorSelects[1], 'lt');

      expect(onChange).toHaveBeenCalledWith([
        { field: 'name', operator: 'eq', value: 'John' },
        { field: 'age', operator: 'lt', value: 25 },
      ]);
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA attributes', () => {
      const onChange = vi.fn();

      render(
        <FilterBuilder
          fields={mockFields}
          value={[]}
          onChange={onChange}
        />
      );

      const container = screen.getByTestId('emf-filter-builder');
      expect(container).toHaveAttribute('role', 'group');
      expect(container).toHaveAttribute('aria-label', 'Filter builder');
    });

    it('should have accessible labels for all inputs', () => {
      const onChange = vi.fn();
      const existingFilters: FilterExpression[] = [
        { field: 'name', operator: 'eq', value: '' },
      ];

      render(
        <FilterBuilder
          fields={mockFields}
          value={existingFilters}
          onChange={onChange}
        />
      );

      expect(screen.getByLabelText(/filter field 1/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/filter operator 1/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/filter value for name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/remove filter 1/i)).toBeInTheDocument();
    });

    it('should apply custom className', () => {
      const onChange = vi.fn();

      render(
        <FilterBuilder
          fields={mockFields}
          value={[]}
          onChange={onChange}
          className="custom-class"
        />
      );

      expect(screen.getByTestId('emf-filter-builder')).toHaveClass('custom-class');
    });

    it('should use custom testId', () => {
      const onChange = vi.fn();

      render(
        <FilterBuilder
          fields={mockFields}
          value={[]}
          onChange={onChange}
          testId="my-filter-builder"
        />
      );

      expect(screen.getByTestId('my-filter-builder')).toBeInTheDocument();
    });
  });
});
