import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { LayoutRenderer } from './LayoutRenderer';
import type { PageLayout, FieldDefinition, LayoutSection, LayoutRelatedList } from '@emf/sdk';
import type { FieldRendererFn } from './types';

// ---------------------------------------------------------------------------
// Helpers to build test data
// ---------------------------------------------------------------------------

function makeField(overrides: Partial<FieldDefinition> & { name: string }): FieldDefinition {
  return {
    type: 'string',
    displayName: overrides.name.charAt(0).toUpperCase() + overrides.name.slice(1),
    ...overrides,
  };
}

function makePlacement(
  fieldId: string,
  sortOrder: number,
  column = 0,
  overrides: Record<string, unknown> = {}
) {
  return {
    id: `fp-${fieldId}-${sortOrder}`,
    fieldId,
    fieldName: fieldId,
    columnNumber: column,
    sortOrder,
    requiredOnLayout: false,
    readOnlyOnLayout: false,
    ...overrides,
  };
}

function makeSection(overrides: Partial<LayoutSection> & { id: string }): LayoutSection {
  return {
    heading: 'Section',
    columns: 1,
    sortOrder: 0,
    collapsed: false,
    style: 'DEFAULT',
    sectionType: 'STANDARD',
    fields: [],
    ...overrides,
  };
}

function makeRelatedList(
  overrides: Partial<LayoutRelatedList> & { id: string }
): LayoutRelatedList {
  return {
    relatedCollectionId: 'related-collection',
    relationshipField: 'parentId',
    displayColumns: 'name,status',
    sortDirection: 'ASC' as const,
    rowLimit: 10,
    sortOrder: 0,
    ...overrides,
  };
}

function makeLayout(sections: LayoutSection[], relatedLists: LayoutRelatedList[] = []): PageLayout {
  return {
    id: 'layout-1',
    tenantId: 'tenant-1',
    collectionId: 'col-1',
    name: 'Test Layout',
    layoutType: 'DETAIL',
    isDefault: true,
    sections,
    relatedLists,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };
}

// ---------------------------------------------------------------------------
// Shared mock data
// ---------------------------------------------------------------------------

const mockFields: FieldDefinition[] = [
  makeField({ id: 'f-name', name: 'name', type: 'string', displayName: 'Full Name' }),
  makeField({ id: 'f-email', name: 'email', type: 'string', displayName: 'Email Address' }),
  makeField({ id: 'f-age', name: 'age', type: 'number', displayName: 'Age' }),
  makeField({ id: 'f-active', name: 'isActive', type: 'boolean', displayName: 'Active Status' }),
  makeField({ id: 'f-created', name: 'createdAt', type: 'date', displayName: 'Created At' }),
  makeField({ id: 'f-status', name: 'status', type: 'string', displayName: 'Status' }),
  makeField({ id: 'f-notes', name: 'notes', type: 'json', displayName: 'Notes' }),
  makeField({ id: 'f-amount', name: 'amount', type: 'currency', displayName: 'Amount' }),
  makeField({ id: 'f-rate', name: 'rate', type: 'percent', displayName: 'Rate' }),
];

const mockRecord: Record<string, unknown> = {
  name: 'John Doe',
  email: 'john@example.com',
  age: 30,
  isActive: true,
  createdAt: '2024-01-15',
  status: 'Active',
  notes: { key: 'value' },
  amount: 100.5,
  rate: 42,
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('LayoutRenderer', () => {
  describe('Basic View Mode Rendering', () => {
    it('should render sections and field values in view mode', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'General Information',
          sortOrder: 0,
          fields: [
            makePlacement('f-name', 0),
            makePlacement('f-email', 1),
            makePlacement('f-age', 2),
          ],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('emf-layout-renderer')).toBeInTheDocument();
      expect(screen.getByText('General Information')).toBeInTheDocument();
      expect(screen.getByText('Full Name')).toBeInTheDocument();
      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('Email Address')).toBeInTheDocument();
      expect(screen.getByText('john@example.com')).toBeInTheDocument();
      expect(screen.getByText('Age')).toBeInTheDocument();
      expect(screen.getByText('30')).toBeInTheDocument();
    });

    it('should apply view mode class', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const container = screen.getByTestId('emf-layout-renderer');
      expect(container).toHaveClass('emf-layout-renderer--view');
    });

    it('should render boolean field as Yes when true', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-active', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText('Yes')).toBeInTheDocument();
    });

    it('should render boolean field as No when false', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-active', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={{ ...mockRecord, isActive: false }}
          fields={mockFields}
          mode="view"
        />
      );

      expect(screen.getByText('No')).toBeInTheDocument();
    });

    it('should render currency field with dollar sign', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Financial',
          fields: [makePlacement('f-amount', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText('$100.50')).toBeInTheDocument();
    });

    it('should render percent field with percent sign', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-rate', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText('42%')).toBeInTheDocument();
    });

    it('should render sections in sort order', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-2',
          heading: 'Second Section',
          sortOrder: 1,
          fields: [makePlacement('f-email', 0)],
        }),
        makeSection({
          id: 'sec-1',
          heading: 'First Section',
          sortOrder: 0,
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const headings = screen.getAllByRole('heading', { level: 3 });
      expect(headings[0]).toHaveTextContent('First Section');
      expect(headings[1]).toHaveTextContent('Second Section');
    });

    it('should render fields within a section sorted by sortOrder', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [
            makePlacement('f-email', 2),
            makePlacement('f-name', 0),
            makePlacement('f-age', 1),
          ],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const fieldElements = screen
        .getByTestId('layout-section-sec-1')
        .querySelectorAll('[data-testid^="layout-field-"]');
      expect(fieldElements[0]).toHaveAttribute('data-field', 'name');
      expect(fieldElements[1]).toHaveAttribute('data-field', 'age');
      expect(fieldElements[2]).toHaveAttribute('data-field', 'email');
    });

    it('should use labelOverride when provided', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { labelOverride: 'Nombre Completo' })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText('Nombre Completo')).toBeInTheDocument();
      expect(screen.queryByText('Full Name')).not.toBeInTheDocument();
    });

    it('should render help text when provided', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [
            makePlacement('f-name', 0, 0, { helpTextOverride: 'Enter your full legal name' }),
          ],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText('Enter your full legal name')).toBeInTheDocument();
    });
  });

  describe('Basic Edit Mode Rendering', () => {
    it('should render text inputs for string fields in edit mode', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0), makePlacement('f-email', 1)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={vi.fn()}
        />
      );

      const nameInput = screen.getByLabelText('Full Name');
      expect(nameInput).toBeInTheDocument();
      expect(nameInput).toHaveAttribute('type', 'text');
      expect(nameInput).toHaveValue('John Doe');

      const emailInput = screen.getByLabelText('Email Address');
      expect(emailInput).toHaveValue('john@example.com');
    });

    it('should apply edit mode class', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={vi.fn()}
        />
      );

      const container = screen.getByTestId('emf-layout-renderer');
      expect(container).toHaveClass('emf-layout-renderer--edit');
    });

    it('should render checkbox for boolean fields in edit mode', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-active', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={vi.fn()}
        />
      );

      const checkbox = screen.getByLabelText('Active Status');
      expect(checkbox).toHaveAttribute('type', 'checkbox');
      expect(checkbox).toBeChecked();
    });

    it('should render number input for number fields in edit mode', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-age', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={vi.fn()}
        />
      );

      const ageInput = screen.getByLabelText('Age');
      expect(ageInput).toHaveAttribute('type', 'number');
      expect(ageInput).toHaveValue(30);
    });

    it('should render date input for date fields in edit mode', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-created', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={vi.fn()}
        />
      );

      const dateInput = screen.getByLabelText('Created At');
      expect(dateInput).toHaveAttribute('type', 'date');
    });

    it('should render textarea for json fields in edit mode', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-notes', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={vi.fn()}
        />
      );

      const textarea = screen.getByLabelText('Notes');
      expect(textarea.tagName).toBe('TEXTAREA');
    });

    it('should call onChange when a text input value changes', () => {
      const onChange = vi.fn();
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={onChange}
        />
      );

      const nameInput = screen.getByLabelText('Full Name');
      fireEvent.change(nameInput, { target: { value: 'Jane Doe' } });

      expect(onChange).toHaveBeenCalledWith('name', 'Jane Doe');
    });

    it('should call onChange when a checkbox value changes', () => {
      const onChange = vi.fn();
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-active', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={onChange}
        />
      );

      const checkbox = screen.getByLabelText('Active Status');
      fireEvent.click(checkbox);

      expect(onChange).toHaveBeenCalledWith('isActive', false);
    });

    it('should show required indicator for required fields in edit mode', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { requiredOnLayout: true })],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={vi.fn()}
        />
      );

      const required = screen.getByText('*');
      expect(required).toBeInTheDocument();
    });

    it('should not show required indicator in view mode', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { requiredOnLayout: true })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.queryByText('*')).not.toBeInTheDocument();
    });

    it('should render read-only fields as view mode even in edit mode', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { readOnlyOnLayout: true })],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={vi.fn()}
        />
      );

      // Read-only fields should render as text, not input
      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.queryByLabelText('Full Name')).not.toBeInTheDocument();
    });
  });

  describe('Multi-Column Section Rendering', () => {
    it('should render fields grouped into the correct number of columns', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Two Column Section',
          columns: 2,
          fields: [
            makePlacement('f-name', 0, 0),
            makePlacement('f-email', 1, 0),
            makePlacement('f-age', 0, 1),
            makePlacement('f-status', 1, 1),
          ],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const sectionBody = screen
        .getByTestId('layout-section-sec-1')
        .querySelector('.emf-layout-renderer__section-body');
      expect(sectionBody).toBeInTheDocument();

      const columns = sectionBody!.querySelectorAll('.emf-layout-renderer__column');
      expect(columns).toHaveLength(2);
    });

    it('should place fields in column 0 when columnNumber is within range', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Multi Col',
          columns: 2,
          fields: [makePlacement('f-name', 0, 0), makePlacement('f-age', 0, 1)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const columns = screen
        .getByTestId('layout-section-sec-1')
        .querySelectorAll('.emf-layout-renderer__column');

      // Column 0 should contain name
      expect(columns[0].querySelector('[data-field="name"]')).toBeInTheDocument();
      // Column 1 should contain age
      expect(columns[1].querySelector('[data-field="age"]')).toBeInTheDocument();
    });

    it('should clamp columnNumber to max column index', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Single Column',
          columns: 1,
          fields: [
            // columnNumber 5 exceeds columns (1), should be clamped to 0
            makePlacement('f-name', 0, 5),
          ],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });

    it('should set grid-template-columns style based on column count', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Three Columns',
          columns: 3,
          fields: [makePlacement('f-name', 0, 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const sectionBody = screen
        .getByTestId('layout-section-sec-1')
        .querySelector('.emf-layout-renderer__section-body');
      expect(sectionBody).toHaveStyle({ gridTemplateColumns: 'repeat(3, 1fr)' });
    });
  });

  describe('Visibility Rule Evaluation', () => {
    it('should show fields when visibility rule evaluates to true (EQUALS)', () => {
      const visibilityRule = JSON.stringify({
        fieldName: 'status',
        operator: 'EQUALS',
        value: 'Active',
      });

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { visibilityRule })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
    });

    it('should hide fields when visibility rule evaluates to false (EQUALS)', () => {
      const visibilityRule = JSON.stringify({
        fieldName: 'status',
        operator: 'EQUALS',
        value: 'Inactive',
      });

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { visibilityRule })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.queryByTestId('layout-field-name')).not.toBeInTheDocument();
    });

    it('should evaluate NOT_EQUALS operator correctly', () => {
      const visibilityRule = JSON.stringify({
        fieldName: 'status',
        operator: 'NOT_EQUALS',
        value: 'Inactive',
      });

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { visibilityRule })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      // status === 'Active', NOT_EQUALS 'Inactive' => true => visible
      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
    });

    it('should evaluate CONTAINS operator correctly', () => {
      const visibilityRule = JSON.stringify({
        fieldName: 'name',
        operator: 'CONTAINS',
        value: 'John',
      });

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-email', 0, 0, { visibilityRule })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('layout-field-email')).toBeInTheDocument();
    });

    it('should evaluate IS_EMPTY operator correctly', () => {
      const visibilityRule = JSON.stringify({
        fieldName: 'missingField',
        operator: 'IS_EMPTY',
      });

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { visibilityRule })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      // missingField is undefined => string empty => IS_EMPTY => true => visible
      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
    });

    it('should evaluate IS_NOT_EMPTY operator correctly', () => {
      const visibilityRule = JSON.stringify({
        fieldName: 'name',
        operator: 'IS_NOT_EMPTY',
      });

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-email', 0, 0, { visibilityRule })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      // name is 'John Doe' => IS_NOT_EMPTY => true => visible
      expect(screen.getByTestId('layout-field-email')).toBeInTheDocument();
    });

    it('should show fields when no visibility rule is set', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
    });

    it('should show fields when visibility rule JSON is invalid', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { visibilityRule: '{invalid json' })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      // Invalid JSON => parsing fails => no rule => default visible
      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
    });

    it('should hide sections when section visibility rule evaluates to false', () => {
      const visibilityRule = JSON.stringify({
        fieldName: 'status',
        operator: 'EQUALS',
        value: 'Inactive',
      });

      const layout = makeLayout([
        makeSection({
          id: 'sec-hidden',
          heading: 'Hidden Section',
          sortOrder: 0,
          visibilityRule,
          fields: [makePlacement('f-name', 0)],
        }),
        makeSection({
          id: 'sec-visible',
          heading: 'Visible Section',
          sortOrder: 1,
          fields: [makePlacement('f-email', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.queryByTestId('layout-section-sec-hidden')).not.toBeInTheDocument();
      expect(screen.getByTestId('layout-section-sec-visible')).toBeInTheDocument();
    });

    it('should handle fieldId key in visibility rule as alternate to fieldName', () => {
      const visibilityRule = JSON.stringify({
        fieldId: 'status',
        operator: 'EQUALS',
        value: 'Active',
      });

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0, 0, { visibilityRule })],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
    });
  });

  describe('Tab Group Rendering', () => {
    it('should render tab group with tab buttons for sections sharing a tabGroup', () => {
      const layout = makeLayout([
        makeSection({
          id: 'tab-1',
          heading: 'Details',
          tabGroup: 'main-tabs',
          tabLabel: 'Details',
          sortOrder: 0,
          fields: [makePlacement('f-name', 0)],
        }),
        makeSection({
          id: 'tab-2',
          heading: 'Contact',
          tabGroup: 'main-tabs',
          tabLabel: 'Contact',
          sortOrder: 1,
          fields: [makePlacement('f-email', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('layout-tab-group')).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'Details' })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'Contact' })).toBeInTheDocument();
    });

    it('should show first tab content by default', () => {
      const layout = makeLayout([
        makeSection({
          id: 'tab-1',
          heading: 'Details',
          tabGroup: 'main-tabs',
          tabLabel: 'Details',
          sortOrder: 0,
          fields: [makePlacement('f-name', 0)],
        }),
        makeSection({
          id: 'tab-2',
          heading: 'Contact',
          tabGroup: 'main-tabs',
          tabLabel: 'Contact',
          sortOrder: 1,
          fields: [makePlacement('f-email', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
      expect(screen.queryByTestId('layout-field-email')).not.toBeInTheDocument();
    });

    it('should switch tab content when clicking on another tab', () => {
      const layout = makeLayout([
        makeSection({
          id: 'tab-1',
          heading: 'Details',
          tabGroup: 'main-tabs',
          tabLabel: 'Details',
          sortOrder: 0,
          fields: [makePlacement('f-name', 0)],
        }),
        makeSection({
          id: 'tab-2',
          heading: 'Contact',
          tabGroup: 'main-tabs',
          tabLabel: 'Contact',
          sortOrder: 1,
          fields: [makePlacement('f-email', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      // Initially showing Details tab
      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();

      // Click Contact tab
      fireEvent.click(screen.getByRole('tab', { name: 'Contact' }));

      // Now Contact tab content should be visible
      expect(screen.getByTestId('layout-field-email')).toBeInTheDocument();
      expect(screen.queryByTestId('layout-field-name')).not.toBeInTheDocument();
    });

    it('should mark the active tab with aria-selected', () => {
      const layout = makeLayout([
        makeSection({
          id: 'tab-1',
          heading: 'Details',
          tabGroup: 'main-tabs',
          tabLabel: 'Details',
          sortOrder: 0,
          fields: [makePlacement('f-name', 0)],
        }),
        makeSection({
          id: 'tab-2',
          heading: 'Contact',
          tabGroup: 'main-tabs',
          tabLabel: 'Contact',
          sortOrder: 1,
          fields: [makePlacement('f-email', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const detailsTab = screen.getByRole('tab', { name: 'Details' });
      const contactTab = screen.getByRole('tab', { name: 'Contact' });

      expect(detailsTab).toHaveAttribute('aria-selected', 'true');
      expect(contactTab).toHaveAttribute('aria-selected', 'false');

      fireEvent.click(contactTab);

      expect(detailsTab).toHaveAttribute('aria-selected', 'false');
      expect(contactTab).toHaveAttribute('aria-selected', 'true');
    });

    it('should use heading when tabLabel is not provided', () => {
      const layout = makeLayout([
        makeSection({
          id: 'tab-1',
          heading: 'Details Heading',
          tabGroup: 'main-tabs',
          sortOrder: 0,
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByRole('tab', { name: 'Details Heading' })).toBeInTheDocument();
    });

    it('should not render hidden tab sections in the tab group', () => {
      const hiddenRule = JSON.stringify({
        fieldName: 'status',
        operator: 'EQUALS',
        value: 'Inactive',
      });

      const layout = makeLayout([
        makeSection({
          id: 'tab-1',
          heading: 'Visible Tab',
          tabGroup: 'main-tabs',
          tabLabel: 'Visible',
          sortOrder: 0,
          fields: [makePlacement('f-name', 0)],
        }),
        makeSection({
          id: 'tab-2',
          heading: 'Hidden Tab',
          tabGroup: 'main-tabs',
          tabLabel: 'Hidden',
          sortOrder: 1,
          visibilityRule: hiddenRule,
          fields: [makePlacement('f-email', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByRole('tab', { name: 'Visible' })).toBeInTheDocument();
      expect(screen.queryByRole('tab', { name: 'Hidden' })).not.toBeInTheDocument();
    });
  });

  describe('Highlights Panel Rendering', () => {
    it('should render highlights panel section before other sections', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-standard',
          heading: 'Standard Section',
          sortOrder: 0,
          sectionType: 'STANDARD',
          fields: [makePlacement('f-email', 0)],
        }),
        makeSection({
          id: 'sec-highlights',
          heading: 'Key Info',
          sortOrder: 1,
          sectionType: 'HIGHLIGHTS_PANEL',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const container = screen.getByTestId('emf-layout-renderer');
      const sections = container.querySelectorAll('.emf-layout-renderer__section');

      // Highlights panel should come first regardless of sort order
      expect(sections[0]).toHaveClass('emf-layout-renderer__section--highlights');
    });

    it('should apply highlights class to highlights panel section', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-highlights',
          heading: 'Key Info',
          sectionType: 'HIGHLIGHTS_PANEL',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const section = screen.getByTestId('layout-section-sec-highlights');
      expect(section).toHaveClass('emf-layout-renderer__section--highlights');
    });

    it('should hide highlights panel when its visibility rule evaluates to false', () => {
      const visibilityRule = JSON.stringify({
        fieldName: 'status',
        operator: 'EQUALS',
        value: 'Inactive',
      });

      const layout = makeLayout([
        makeSection({
          id: 'sec-highlights',
          heading: 'Key Info',
          sectionType: 'HIGHLIGHTS_PANEL',
          visibilityRule,
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.queryByTestId('layout-section-sec-highlights')).not.toBeInTheDocument();
    });

    it('should not include highlights panel in standalone sections list', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-highlights',
          heading: 'Highlights',
          sortOrder: 0,
          sectionType: 'HIGHLIGHTS_PANEL',
          fields: [makePlacement('f-name', 0)],
        }),
        makeSection({
          id: 'sec-standard',
          heading: 'Details',
          sortOrder: 1,
          sectionType: 'STANDARD',
          fields: [makePlacement('f-email', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      // Should only have two sections total (highlights + 1 standard)
      const allSections = screen.getByTestId('emf-layout-renderer').querySelectorAll('section');
      expect(allSections).toHaveLength(2);
    });
  });

  describe('Collapsible Section Toggling', () => {
    it('should render a toggle button for collapsible sections', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-collapsible',
          heading: 'Collapsible Section',
          style: 'COLLAPSIBLE',
          collapsed: false,
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const toggle = screen.getByTestId('layout-section-toggle-sec-collapsible');
      expect(toggle).toBeInTheDocument();
      expect(toggle).toHaveAttribute('aria-expanded', 'true');
    });

    it('should apply collapsible class to section', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-collapsible',
          heading: 'Collapsible',
          style: 'COLLAPSIBLE',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const section = screen.getByTestId('layout-section-sec-collapsible');
      expect(section).toHaveClass('emf-layout-renderer__section--collapsible');
    });

    it('should hide content when section starts collapsed', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-collapsible',
          heading: 'Collapsible',
          style: 'COLLAPSIBLE',
          collapsed: true,
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.queryByTestId('layout-field-name')).not.toBeInTheDocument();
    });

    it('should show content when section starts expanded', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-collapsible',
          heading: 'Collapsible',
          style: 'COLLAPSIBLE',
          collapsed: false,
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
    });

    it('should toggle content visibility when clicking the toggle button', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-collapsible',
          heading: 'Collapsible',
          style: 'COLLAPSIBLE',
          collapsed: false,
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const toggle = screen.getByTestId('layout-section-toggle-sec-collapsible');

      // Initially expanded
      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
      expect(toggle).toHaveAttribute('aria-expanded', 'true');

      // Click to collapse
      fireEvent.click(toggle);
      expect(screen.queryByTestId('layout-field-name')).not.toBeInTheDocument();
      expect(toggle).toHaveAttribute('aria-expanded', 'false');

      // Click to expand again
      fireEvent.click(toggle);
      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
      expect(toggle).toHaveAttribute('aria-expanded', 'true');
    });

    it('should not render a toggle button for non-collapsible sections', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-default',
          heading: 'Default Section',
          style: 'DEFAULT',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.queryByTestId('layout-section-toggle-sec-default')).not.toBeInTheDocument();
    });

    it('should render heading as h3 for non-collapsible sections', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-default',
          heading: 'Default Section',
          style: 'DEFAULT',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(
        screen.getByRole('heading', { level: 3, name: 'Default Section' })
      ).toBeInTheDocument();
    });
  });

  describe('Related List Rendering', () => {
    it('should render related lists below sections', () => {
      const layout = makeLayout(
        [
          makeSection({
            id: 'sec-1',
            heading: 'Info',
            fields: [makePlacement('f-name', 0)],
          }),
        ],
        [
          makeRelatedList({
            id: 'rl-1',
            relatedCollectionId: 'contacts',
            relationshipField: 'accountId',
            displayColumns: 'name,phone,email',
            rowLimit: 5,
            sortOrder: 0,
          }),
        ]
      );

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('layout-related-lists')).toBeInTheDocument();
      expect(screen.getByTestId('layout-related-list-rl-1')).toBeInTheDocument();
    });

    it('should display related collection name as heading', () => {
      const layout = makeLayout(
        [],
        [
          makeRelatedList({
            id: 'rl-1',
            relatedCollectionId: 'contacts',
            relationshipField: 'accountId',
            displayColumns: 'name,phone',
            sortOrder: 0,
          }),
        ]
      );

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText('contacts')).toBeInTheDocument();
    });

    it('should display relationship field info', () => {
      const layout = makeLayout(
        [],
        [
          makeRelatedList({
            id: 'rl-1',
            relatedCollectionId: 'contacts',
            relationshipField: 'accountId',
            displayColumns: 'name,phone',
            sortOrder: 0,
          }),
        ]
      );

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText(/Relationship: accountId/)).toBeInTheDocument();
    });

    it('should display column names from displayColumns', () => {
      const layout = makeLayout(
        [],
        [
          makeRelatedList({
            id: 'rl-1',
            relatedCollectionId: 'contacts',
            relationshipField: 'accountId',
            displayColumns: 'name,phone,email',
            sortOrder: 0,
          }),
        ]
      );

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText(/Columns: name, phone, email/)).toBeInTheDocument();
    });

    it('should display row limit', () => {
      const layout = makeLayout(
        [],
        [
          makeRelatedList({
            id: 'rl-1',
            relatedCollectionId: 'contacts',
            relationshipField: 'accountId',
            displayColumns: 'name',
            rowLimit: 25,
            sortOrder: 0,
          }),
        ]
      );

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText(/Limit: 25/)).toBeInTheDocument();
    });

    it('should render multiple related lists in sort order', () => {
      const layout = makeLayout(
        [],
        [
          makeRelatedList({
            id: 'rl-2',
            relatedCollectionId: 'activities',
            relationshipField: 'parentId',
            displayColumns: 'subject',
            sortOrder: 1,
          }),
          makeRelatedList({
            id: 'rl-1',
            relatedCollectionId: 'contacts',
            relationshipField: 'accountId',
            displayColumns: 'name',
            sortOrder: 0,
          }),
        ]
      );

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const relatedListContainer = screen.getByTestId('layout-related-lists');
      const lists = relatedListContainer.querySelectorAll('.emf-layout-renderer__related-list');
      expect(lists).toHaveLength(2);

      // Should be sorted: contacts (0) before activities (1)
      expect(lists[0]).toHaveAttribute('data-testid', 'layout-related-list-rl-1');
      expect(lists[1]).toHaveAttribute('data-testid', 'layout-related-list-rl-2');
    });

    it('should not render related lists container when there are no related lists', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.queryByTestId('layout-related-lists')).not.toBeInTheDocument();
    });
  });

  describe('Custom Field Renderers', () => {
    it('should use custom renderer when provided for a field', () => {
      const customRenderers: Record<string, FieldRendererFn> = {
        name: (value) => <span data-testid="custom-name-renderer">Custom: {String(value)}</span>,
      };

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0), makePlacement('f-email', 1)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="view"
          customRenderers={customRenderers}
        />
      );

      expect(screen.getByTestId('custom-name-renderer')).toBeInTheDocument();
      expect(screen.getByText('Custom: John Doe')).toBeInTheDocument();
      // Email should use default renderer
      expect(screen.getByText('john@example.com')).toBeInTheDocument();
    });

    it('should pass correct arguments to custom renderer in view mode', () => {
      const customRenderer = vi.fn((value, _field, mode) => (
        <span data-testid="custom-render">
          {String(value)} ({mode})
        </span>
      ));

      const customRenderers: Record<string, FieldRendererFn> = {
        name: customRenderer,
      };

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="view"
          customRenderers={customRenderers}
        />
      );

      expect(customRenderer).toHaveBeenCalledWith(
        'John Doe',
        expect.objectContaining({ name: 'name', type: 'string' }),
        'view',
        undefined
      );
    });

    it('should pass onChange to custom renderer in edit mode', () => {
      const customRenderer = vi.fn((value, _field, mode, onChange) => (
        <input
          data-testid="custom-input"
          value={String(value)}
          onChange={(e) => onChange?.(e.target.value)}
        />
      ));

      const customRenderers: Record<string, FieldRendererFn> = {
        name: customRenderer,
      };

      const onChange = vi.fn();
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="edit"
          onChange={onChange}
          customRenderers={customRenderers}
        />
      );

      expect(customRenderer).toHaveBeenCalledWith(
        'John Doe',
        expect.objectContaining({ name: 'name', type: 'string' }),
        'edit',
        expect.any(Function)
      );
    });

    it('should fall back to default renderer for fields without custom renderers', () => {
      const customRenderers: Record<string, FieldRendererFn> = {
        name: () => <span data-testid="custom-name">Custom Name</span>,
      };

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('f-name', 0), makePlacement('f-age', 1)],
        }),
      ]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="view"
          customRenderers={customRenderers}
        />
      );

      expect(screen.getByTestId('custom-name')).toBeInTheDocument();
      // Age should use default number renderer
      expect(screen.getByText('30')).toBeInTheDocument();
    });
  });

  describe('Empty Layout Handling', () => {
    it('should render container even with no sections', () => {
      const layout = makeLayout([]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('emf-layout-renderer')).toBeInTheDocument();
    });

    it('should render container with no related lists', () => {
      const layout = makeLayout([], []);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByTestId('emf-layout-renderer')).toBeInTheDocument();
      expect(screen.queryByTestId('layout-related-lists')).not.toBeInTheDocument();
    });

    it('should render section with no fields', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-empty',
          heading: 'Empty Section',
          fields: [],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      expect(screen.getByText('Empty Section')).toBeInTheDocument();
      expect(screen.getByTestId('layout-section-sec-empty')).toBeInTheDocument();
    });

    it('should not render field cell when field definition is not found', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [makePlacement('nonexistent-field', 0), makePlacement('f-name', 1)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      // name should still render
      expect(screen.getByTestId('layout-field-name')).toBeInTheDocument();
      // nonexistent field should not render
      expect(screen.queryByTestId('layout-field-nonexistent-field')).not.toBeInTheDocument();
    });
  });

  describe('Custom Styling and Test ID', () => {
    it('should apply custom className', () => {
      const layout = makeLayout([]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="view"
          className="custom-class"
        />
      );

      expect(screen.getByTestId('emf-layout-renderer')).toHaveClass('custom-class');
    });

    it('should use custom testId', () => {
      const layout = makeLayout([]);

      render(
        <LayoutRenderer
          layout={layout}
          record={mockRecord}
          fields={mockFields}
          mode="view"
          testId="my-layout"
        />
      );

      expect(screen.getByTestId('my-layout')).toBeInTheDocument();
    });
  });

  describe('Card Style Section', () => {
    it('should apply card class to card-style sections', () => {
      const layout = makeLayout([
        makeSection({
          id: 'sec-card',
          heading: 'Card Section',
          style: 'CARD',
          fields: [makePlacement('f-name', 0)],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={mockFields} mode="view" />
      );

      const section = screen.getByTestId('layout-section-sec-card');
      expect(section).toHaveClass('emf-layout-renderer__section--card');
    });
  });

  describe('Field Lookup by Name Fallback', () => {
    it('should resolve fields by fieldName when fieldId does not match', () => {
      const fieldsWithoutId: FieldDefinition[] = [
        { name: 'name', type: 'string', displayName: 'Full Name' },
      ];

      const layout = makeLayout([
        makeSection({
          id: 'sec-1',
          heading: 'Info',
          fields: [
            {
              id: 'fp-1',
              fieldId: 'unknown-id',
              fieldName: 'name',
              columnNumber: 0,
              sortOrder: 0,
              requiredOnLayout: false,
              readOnlyOnLayout: false,
            },
          ],
        }),
      ]);

      render(
        <LayoutRenderer layout={layout} record={mockRecord} fields={fieldsWithoutId} mode="view" />
      );

      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });
  });
});
