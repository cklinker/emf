/**
 * FieldEditor Component Tests
 *
 * Tests for the FieldEditor component covering rendering, validation,
 * form submission, edit mode, field types, validation rules, and accessibility.
 *
 * Requirements tested:
 * - 4.2: Display form for entering field details
 * - 4.3: Support all field types: string, number, boolean, date, datetime, json, reference
 * - 4.4: Display dropdown to select target collection for reference fields
 * - 4.5: Add field via API and update field list
 * - 4.6: Display validation errors inline with form fields
 * - 4.7: Pre-populate form with current values in edit mode
 * - 4.11: Allow setting validation rules (required, min, max, pattern, email, url)
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FieldEditor, FieldDefinition, CollectionSummary } from './FieldEditor';
import { I18nProvider } from '../../context/I18nContext';
import { ToastProvider } from '../Toast';

// Wrapper component to provide required contexts
function TestWrapper({ children }: { children: React.ReactNode }) {
  return (
    <I18nProvider>
      <ToastProvider>{children}</ToastProvider>
    </I18nProvider>
  );
}

// Helper to render with contexts
function renderWithProviders(ui: React.ReactElement) {
  return render(ui, { wrapper: TestWrapper });
}

// Mock field for edit mode tests
const mockField: FieldDefinition = {
  id: 'field-123',
  name: 'test_field',
  displayName: 'Test Field',
  type: 'string',
  required: true,
  unique: false,
  indexed: true,
  defaultValue: 'default',
  order: 0,
};


// Mock field with validation rules
const mockFieldWithValidation: FieldDefinition = {
  id: 'field-456',
  name: 'email_field',
  displayName: 'Email Field',
  type: 'string',
  required: true,
  unique: true,
  indexed: false,
  validation: [
    { type: 'email', message: 'Must be a valid email' },
    { type: 'max', value: 100, message: 'Max 100 characters' },
  ],
  order: 1,
};

// Mock reference field
const mockReferenceField: FieldDefinition = {
  id: 'field-789',
  name: 'user_ref',
  displayName: 'User Reference',
  type: 'reference',
  required: false,
  unique: false,
  indexed: true,
  referenceTarget: 'users',
  order: 2,
};

// Mock collections for reference dropdown
const mockCollections: CollectionSummary[] = [
  { id: 'col-1', name: 'users', displayName: 'Users' },
  { id: 'col-2', name: 'products', displayName: 'Products' },
  { id: 'col-3', name: 'orders', displayName: 'Orders' },
];

describe('FieldEditor Component', () => {
  const defaultProps = {
    collectionId: 'col-123',
    onSave: vi.fn().mockResolvedValue(undefined),
    onCancel: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering - Create Mode', () => {
    it('should render all form fields', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('field-editor')).toBeInTheDocument();
      expect(screen.getByTestId('field-name-input')).toBeInTheDocument();
      expect(screen.getByTestId('field-display-name-input')).toBeInTheDocument();
      expect(screen.getByTestId('field-type-select')).toBeInTheDocument();
      expect(screen.getByTestId('field-required-checkbox')).toBeInTheDocument();
      expect(screen.getByTestId('field-unique-checkbox')).toBeInTheDocument();
      expect(screen.getByTestId('field-indexed-checkbox')).toBeInTheDocument();
      expect(screen.getByTestId('field-default-value-input')).toBeInTheDocument();
    });

    it('should render form with empty values in create mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('field-name-input')).toHaveValue('');
      expect(screen.getByTestId('field-display-name-input')).toHaveValue('');
      expect(screen.getByTestId('field-type-select')).toHaveValue('string');
      expect(screen.getByTestId('field-required-checkbox')).not.toBeChecked();
      expect(screen.getByTestId('field-unique-checkbox')).not.toBeChecked();
      expect(screen.getByTestId('field-indexed-checkbox')).not.toBeChecked();
    });

    it('should render submit button with "Create" text in create mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      const submitButton = screen.getByTestId('field-editor-submit');
      expect(submitButton).toHaveTextContent('Create');
    });

    it('should render cancel button', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('field-editor-cancel')).toBeInTheDocument();
    });

    it('should render name field as enabled in create mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('field-name-input')).not.toBeDisabled();
    });

    it('should render type field as enabled in create mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('field-type-select')).not.toBeDisabled();
    });

    it('should render hint text for name field', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('field-name-hint')).toBeInTheDocument();
    });

    it('should render form title for create mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByText('Add Field')).toBeInTheDocument();
    });
  });


  describe('Rendering - Edit Mode', () => {
    it('should pre-populate form with field data', () => {
      renderWithProviders(<FieldEditor {...defaultProps} field={mockField} />);

      expect(screen.getByTestId('field-name-input')).toHaveValue('test_field');
      expect(screen.getByTestId('field-display-name-input')).toHaveValue('Test Field');
      expect(screen.getByTestId('field-type-select')).toHaveValue('string');
      expect(screen.getByTestId('field-required-checkbox')).toBeChecked();
      expect(screen.getByTestId('field-unique-checkbox')).not.toBeChecked();
      expect(screen.getByTestId('field-indexed-checkbox')).toBeChecked();
      expect(screen.getByTestId('field-default-value-input')).toHaveValue('default');
    });

    it('should render submit button with "Save" text in edit mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} field={mockField} />);

      const submitButton = screen.getByTestId('field-editor-submit');
      expect(submitButton).toHaveTextContent('Save');
    });

    it('should disable name field in edit mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} field={mockField} />);

      expect(screen.getByTestId('field-name-input')).toBeDisabled();
    });

    it('should disable type field in edit mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} field={mockField} />);

      expect(screen.getByTestId('field-type-select')).toBeDisabled();
    });

    it('should not render name hint in edit mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} field={mockField} />);

      expect(screen.queryByTestId('field-name-hint')).not.toBeInTheDocument();
    });

    it('should disable submit button when form is not dirty in edit mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} field={mockField} />);

      expect(screen.getByTestId('field-editor-submit')).toBeDisabled();
    });

    it('should enable submit button when form is dirty in edit mode', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} field={mockField} />);

      await user.clear(screen.getByTestId('field-display-name-input'));
      await user.type(screen.getByTestId('field-display-name-input'), 'Updated Name');

      expect(screen.getByTestId('field-editor-submit')).not.toBeDisabled();
    });

    it('should render form title for edit mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} field={mockField} />);

      expect(screen.getByText('Edit Field')).toBeInTheDocument();
    });

    it('should pre-populate validation rules in edit mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} field={mockFieldWithValidation} />);

      expect(screen.getByTestId('validation-rule-0')).toBeInTheDocument();
      expect(screen.getByTestId('validation-rule-1')).toBeInTheDocument();
    });
  });

  describe('Field Types', () => {
    it('should have all field type options', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      const select = screen.getByTestId('field-type-select');
      const options = select.querySelectorAll('option');

      expect(options).toHaveLength(7);
      expect(options[0]).toHaveValue('string');
      expect(options[1]).toHaveValue('number');
      expect(options[2]).toHaveValue('boolean');
      expect(options[3]).toHaveValue('date');
      expect(options[4]).toHaveValue('datetime');
      expect(options[5]).toHaveValue('json');
      expect(options[6]).toHaveValue('reference');
    });

    it('should default to string type in create mode', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('field-type-select')).toHaveValue('string');
    });

    it('should show reference target dropdown when reference type is selected', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} collections={mockCollections} />);

      await user.selectOptions(screen.getByTestId('field-type-select'), 'reference');

      expect(screen.getByTestId('field-reference-target-select')).toBeInTheDocument();
    });

    it('should not show reference target dropdown for non-reference types', () => {
      renderWithProviders(<FieldEditor {...defaultProps} collections={mockCollections} />);

      expect(screen.queryByTestId('field-reference-target-select')).not.toBeInTheDocument();
    });

    it('should populate reference target dropdown with collections', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} collections={mockCollections} />);

      await user.selectOptions(screen.getByTestId('field-type-select'), 'reference');

      const select = screen.getByTestId('field-reference-target-select');
      const options = select.querySelectorAll('option');

      // First option is placeholder, then 3 collections
      expect(options).toHaveLength(4);
      expect(options[1]).toHaveValue('users');
      expect(options[2]).toHaveValue('products');
      expect(options[3]).toHaveValue('orders');
    });

    it('should pre-populate reference target in edit mode', () => {
      renderWithProviders(
        <FieldEditor {...defaultProps} field={mockReferenceField} collections={mockCollections} />
      );

      expect(screen.getByTestId('field-reference-target-select')).toHaveValue('users');
    });
  });


  describe('Validation Rules Section', () => {
    it('should show validation rules section for string type', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByText('Validation Rules')).toBeInTheDocument();
      expect(screen.getByTestId('add-validation-rule-button')).toBeInTheDocument();
    });

    it('should show no validation rules message initially', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('no-validation-rules')).toBeInTheDocument();
    });

    it('should add a validation rule when add button is clicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.click(screen.getByTestId('add-validation-rule-button'));

      expect(screen.getByTestId('validation-rule-0')).toBeInTheDocument();
      expect(screen.queryByTestId('no-validation-rules')).not.toBeInTheDocument();
    });

    it('should remove a validation rule when remove button is clicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.click(screen.getByTestId('add-validation-rule-button'));
      expect(screen.getByTestId('validation-rule-0')).toBeInTheDocument();

      await user.click(screen.getByTestId('remove-validation-rule-0'));
      expect(screen.queryByTestId('validation-rule-0')).not.toBeInTheDocument();
    });

    it('should show value field for min rule', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.click(screen.getByTestId('add-validation-rule-button'));

      expect(screen.getByTestId('validation-rule-value-0')).toBeInTheDocument();
    });

    it('should show value field for max rule', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.click(screen.getByTestId('add-validation-rule-button'));
      await user.selectOptions(screen.getByTestId('validation-rule-type-0'), 'max');

      expect(screen.getByTestId('validation-rule-value-0')).toBeInTheDocument();
    });

    it('should show value field for pattern rule', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.click(screen.getByTestId('add-validation-rule-button'));
      await user.selectOptions(screen.getByTestId('validation-rule-type-0'), 'pattern');

      expect(screen.getByTestId('validation-rule-value-0')).toBeInTheDocument();
    });

    it('should not show validation rules section for boolean type', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.selectOptions(screen.getByTestId('field-type-select'), 'boolean');

      expect(screen.queryByText('Validation Rules')).not.toBeInTheDocument();
    });

    it('should not show validation rules section for json type', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.selectOptions(screen.getByTestId('field-type-select'), 'json');

      expect(screen.queryByText('Validation Rules')).not.toBeInTheDocument();
    });

    it('should not show validation rules section for reference type', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} collections={mockCollections} />);

      await user.selectOptions(screen.getByTestId('field-type-select'), 'reference');

      expect(screen.queryByText('Validation Rules')).not.toBeInTheDocument();
    });

    it('should show only min and max rules for number type', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.selectOptions(screen.getByTestId('field-type-select'), 'number');
      await user.click(screen.getByTestId('add-validation-rule-button'));

      const ruleTypeSelect = screen.getByTestId('validation-rule-type-0');
      const options = ruleTypeSelect.querySelectorAll('option');

      expect(options).toHaveLength(2);
      expect(options[0]).toHaveValue('min');
      expect(options[1]).toHaveValue('max');
    });

    it('should disable add rule button when all rules are added', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.selectOptions(screen.getByTestId('field-type-select'), 'number');
      
      // Add min rule
      await user.click(screen.getByTestId('add-validation-rule-button'));
      // Add max rule
      await user.click(screen.getByTestId('add-validation-rule-button'));

      // Button should be disabled now (only 2 rules for number)
      expect(screen.getByTestId('add-validation-rule-button')).toBeDisabled();
    });
  });


  describe('Validation - Name Field', () => {
    it('should show error when name is empty', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      const nameInput = screen.getByTestId('field-name-input');
      await user.click(nameInput);
      await user.tab();

      await waitFor(() => {
        expect(screen.getByTestId('field-name-error')).toBeInTheDocument();
      });
    });

    it('should show error when name contains uppercase letters', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.type(screen.getByTestId('field-name-input'), 'TestField');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByTestId('field-name-error')).toBeInTheDocument();
      });
    });

    it('should show error when name starts with a number', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.type(screen.getByTestId('field-name-input'), '123field');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByTestId('field-name-error')).toBeInTheDocument();
      });
    });

    it('should show error when name contains special characters', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.type(screen.getByTestId('field-name-input'), 'test-field');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByTestId('field-name-error')).toBeInTheDocument();
      });
    });

    it('should accept valid name with lowercase and underscores', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.type(screen.getByTestId('field-name-input'), 'valid_field_name');
      await user.tab();

      await waitFor(() => {
        expect(screen.queryByTestId('field-name-error')).not.toBeInTheDocument();
      });
    });

    it('should mark name input as invalid when error exists', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.click(screen.getByTestId('field-name-input'));
      await user.tab();

      await waitFor(() => {
        expect(screen.getByTestId('field-name-input')).toHaveAttribute('aria-invalid', 'true');
      });
    });
  });

  describe('Validation - Reference Target', () => {
    it('should show error when reference type is selected without target', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} collections={mockCollections} />);

      await user.type(screen.getByTestId('field-name-input'), 'my_ref');
      await user.selectOptions(screen.getByTestId('field-type-select'), 'reference');
      
      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(screen.getByTestId('field-reference-target-error')).toBeInTheDocument();
      });
    });

    it('should not show error when reference target is selected', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} collections={mockCollections} />);

      await user.type(screen.getByTestId('field-name-input'), 'my_ref');
      await user.selectOptions(screen.getByTestId('field-type-select'), 'reference');
      await user.selectOptions(screen.getByTestId('field-reference-target-select'), 'users');
      await user.tab();

      await waitFor(() => {
        expect(screen.queryByTestId('field-reference-target-error')).not.toBeInTheDocument();
      });
    });
  });


  describe('Form Submission - Create Mode', () => {
    it('should call onSave with form data when valid', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} onSave={onSave} />);

      await user.type(screen.getByTestId('field-name-input'), 'my_field');
      await user.type(screen.getByTestId('field-display-name-input'), 'My Field');
      await user.click(screen.getByTestId('field-required-checkbox'));

      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledTimes(1);
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            name: 'my_field',
            displayName: 'My Field',
            type: 'string',
            required: true,
            unique: false,
            indexed: false,
          })
        );
      });
    });

    it('should not call onSave when form is invalid', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} onSave={onSave} />);

      // Submit without filling required fields
      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).not.toHaveBeenCalled();
      });
    });

    it('should submit with validation rules', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} onSave={onSave} />);

      await user.type(screen.getByTestId('field-name-input'), 'my_field');
      await user.click(screen.getByTestId('add-validation-rule-button'));
      await user.type(screen.getByTestId('validation-rule-value-0'), '5');
      await user.type(screen.getByTestId('validation-rule-message-0'), 'Min 5 chars');

      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            validation: [
              expect.objectContaining({
                type: 'min',
                value: 5,
                message: 'Min 5 chars',
              }),
            ],
          })
        );
      });
    });

    it('should submit reference field with target', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} onSave={onSave} collections={mockCollections} />);

      await user.type(screen.getByTestId('field-name-input'), 'user_ref');
      await user.selectOptions(screen.getByTestId('field-type-select'), 'reference');
      await user.selectOptions(screen.getByTestId('field-reference-target-select'), 'users');

      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            type: 'reference',
            referenceTarget: 'users',
          })
        );
      });
    });

    it('should generate unique ID for new field', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} onSave={onSave} />);

      await user.type(screen.getByTestId('field-name-input'), 'my_field');
      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            id: expect.stringMatching(/^field_\d+_[a-z0-9]+$/),
          })
        );
      });
    });

    it('should submit with default value', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} onSave={onSave} />);

      await user.type(screen.getByTestId('field-name-input'), 'my_field');
      await user.type(screen.getByTestId('field-default-value-input'), 'default_value');

      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            defaultValue: 'default_value',
          })
        );
      });
    });

    it('should parse number default value for number type', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} onSave={onSave} />);

      await user.type(screen.getByTestId('field-name-input'), 'my_number');
      await user.selectOptions(screen.getByTestId('field-type-select'), 'number');
      await user.type(screen.getByTestId('field-default-value-input'), '42');

      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            defaultValue: 42,
          })
        );
      });
    });

    it('should parse boolean default value for boolean type', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} onSave={onSave} />);

      await user.type(screen.getByTestId('field-name-input'), 'my_bool');
      await user.selectOptions(screen.getByTestId('field-type-select'), 'boolean');
      await user.type(screen.getByTestId('field-default-value-input'), 'true');

      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            defaultValue: true,
          })
        );
      });
    });
  });


  describe('Form Submission - Edit Mode', () => {
    it('should call onSave with updated data', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(
        <FieldEditor {...defaultProps} field={mockField} onSave={onSave} />
      );

      await user.clear(screen.getByTestId('field-display-name-input'));
      await user.type(screen.getByTestId('field-display-name-input'), 'Updated Field');

      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            id: 'field-123',
            name: 'test_field',
            displayName: 'Updated Field',
          })
        );
      });
    });

    it('should preserve field ID in edit mode', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      renderWithProviders(
        <FieldEditor {...defaultProps} field={mockField} onSave={onSave} />
      );

      await user.click(screen.getByTestId('field-unique-checkbox'));
      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            id: 'field-123',
          })
        );
      });
    });

    it('should preserve field order in edit mode', async () => {
      const onSave = vi.fn().mockResolvedValue(undefined);
      const user = userEvent.setup();
      const fieldWithOrder = { ...mockField, order: 5 };
      renderWithProviders(
        <FieldEditor {...defaultProps} field={fieldWithOrder} onSave={onSave} />
      );

      await user.click(screen.getByTestId('field-unique-checkbox'));
      await user.click(screen.getByTestId('field-editor-submit'));

      await waitFor(() => {
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({
            order: 5,
          })
        );
      });
    });
  });

  describe('Loading State', () => {
    it('should disable all inputs when submitting', () => {
      renderWithProviders(<FieldEditor {...defaultProps} isSubmitting={true} />);

      expect(screen.getByTestId('field-name-input')).toBeDisabled();
      expect(screen.getByTestId('field-display-name-input')).toBeDisabled();
      expect(screen.getByTestId('field-type-select')).toBeDisabled();
      expect(screen.getByTestId('field-required-checkbox')).toBeDisabled();
      expect(screen.getByTestId('field-unique-checkbox')).toBeDisabled();
      expect(screen.getByTestId('field-indexed-checkbox')).toBeDisabled();
      expect(screen.getByTestId('field-default-value-input')).toBeDisabled();
    });

    it('should disable buttons when submitting', () => {
      renderWithProviders(<FieldEditor {...defaultProps} isSubmitting={true} />);

      expect(screen.getByTestId('field-editor-submit')).toBeDisabled();
      expect(screen.getByTestId('field-editor-cancel')).toBeDisabled();
    });

    it('should show loading spinner in submit button when submitting', () => {
      renderWithProviders(<FieldEditor {...defaultProps} isSubmitting={true} />);

      const submitButton = screen.getByTestId('field-editor-submit');
      expect(submitButton.querySelector('[data-testid="loading-spinner"]')).toBeInTheDocument();
    });
  });

  describe('Cancel Action', () => {
    it('should call onCancel when cancel button is clicked', async () => {
      const onCancel = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} onCancel={onCancel} />);

      await user.click(screen.getByTestId('field-editor-cancel'));

      expect(onCancel).toHaveBeenCalledTimes(1);
    });
  });


  describe('Accessibility', () => {
    it('should have proper labels for all inputs', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByLabelText(/Field Name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Display Name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Field Type/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Required/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Unique/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Indexed/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Default Value/i)).toBeInTheDocument();
    });

    it('should have aria-required on required fields', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('field-name-input')).toHaveAttribute('aria-required', 'true');
      expect(screen.getByTestId('field-type-select')).toHaveAttribute('aria-required', 'true');
    });

    it('should have aria-describedby pointing to error when error exists', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.click(screen.getByTestId('field-name-input'));
      await user.tab();

      await waitFor(() => {
        expect(screen.getByTestId('field-name-input')).toHaveAttribute(
          'aria-describedby',
          'field-name-error'
        );
      });
    });

    it('should have role="alert" on error messages', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.click(screen.getByTestId('field-name-input'));
      await user.tab();

      await waitFor(() => {
        expect(screen.getByTestId('field-name-error')).toHaveAttribute('role', 'alert');
      });
    });

    it('should have noValidate on form to use custom validation', () => {
      renderWithProviders(<FieldEditor {...defaultProps} />);

      expect(screen.getByTestId('field-editor')).toHaveAttribute('noValidate');
    });

    it('should have aria-label on remove rule buttons', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FieldEditor {...defaultProps} />);

      await user.click(screen.getByTestId('add-validation-rule-button'));

      expect(screen.getByTestId('remove-validation-rule-0')).toHaveAttribute('aria-label');
    });
  });

  describe('Form Reset on Field Change', () => {
    it('should reset form when field prop changes', async () => {
      const { rerender } = renderWithProviders(
        <FieldEditor {...defaultProps} field={mockField} />
      );

      expect(screen.getByTestId('field-display-name-input')).toHaveValue('Test Field');

      const updatedField: FieldDefinition = {
        ...mockField,
        displayName: 'Updated Field Name',
      };

      rerender(
        <TestWrapper>
          <FieldEditor {...defaultProps} field={updatedField} />
        </TestWrapper>
      );

      expect(screen.getByTestId('field-display-name-input')).toHaveValue('Updated Field Name');
    });
  });
});

describe('FieldEditor Integration', () => {
  it('should work with async submission', async () => {
    const onSave = vi.fn().mockImplementation(
      () => new Promise((resolve) => setTimeout(resolve, 100))
    );
    const user = userEvent.setup();

    const TestComponent = () => {
      const [isSubmitting, setIsSubmitting] = React.useState(false);

      const handleSave = async (data: FieldDefinition) => {
        setIsSubmitting(true);
        try {
          await onSave(data);
        } finally {
          setIsSubmitting(false);
        }
      };

      return (
        <FieldEditor
          collectionId="col-123"
          onSave={handleSave}
          onCancel={() => {}}
          isSubmitting={isSubmitting}
        />
      );
    };

    renderWithProviders(<TestComponent />);

    await user.type(screen.getByTestId('field-name-input'), 'my_field');
    await user.click(screen.getByTestId('field-editor-submit'));

    // Should show loading state
    await waitFor(() => {
      expect(screen.getByTestId('field-editor-submit')).toBeDisabled();
    });

    // Should complete submission
    await waitFor(() => {
      expect(onSave).toHaveBeenCalled();
    });
  });
});
