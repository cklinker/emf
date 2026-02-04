/**
 * LoadingSpinner Component Tests
 *
 * Tests for the LoadingSpinner component covering:
 * - Rendering with default props
 * - Size variants (small, medium, large)
 * - Label display
 * - Accessibility attributes (role, aria-live, aria-busy)
 * - Screen reader text
 * - Custom className support
 * - Custom data-testid support
 */

import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { LoadingSpinner } from './LoadingSpinner';
import type { SpinnerSize } from './LoadingSpinner';

describe('LoadingSpinner', () => {
  describe('Rendering', () => {
    it('renders with default props', () => {
      render(<LoadingSpinner />);

      const spinner = screen.getByTestId('loading-spinner');
      expect(spinner).toBeInTheDocument();
    });

    it('renders the spinner icon', () => {
      render(<LoadingSpinner />);

      const spinnerIcon = screen.getByTestId('loading-spinner-icon');
      expect(spinnerIcon).toBeInTheDocument();
    });

    it('renders screen reader text with default message', () => {
      render(<LoadingSpinner />);

      const srText = screen.getByTestId('loading-spinner-sr-text');
      expect(srText).toBeInTheDocument();
      expect(srText).toHaveTextContent('Loading...');
    });
  });

  describe('Size Variants', () => {
    const sizes: SpinnerSize[] = ['small', 'medium', 'large'];

    sizes.forEach((size) => {
      it(`renders with size="${size}"`, () => {
        render(<LoadingSpinner size={size} />);

        const spinnerIcon = screen.getByTestId('loading-spinner-icon');
        expect(spinnerIcon).toBeInTheDocument();
        // CSS Modules transform class names, so we check for partial match
        expect(spinnerIcon.className).toMatch(new RegExp(size));
      });
    });

    it('defaults to medium size when no size is specified', () => {
      render(<LoadingSpinner />);

      const spinnerIcon = screen.getByTestId('loading-spinner-icon');
      // CSS Modules transform class names, so we check for partial match
      expect(spinnerIcon.className).toMatch(/medium/);
    });
  });

  describe('Label', () => {
    it('renders visible label when provided', () => {
      const labelText = 'Loading data...';
      render(<LoadingSpinner label={labelText} />);

      const label = screen.getByTestId('loading-spinner-label');
      expect(label).toBeInTheDocument();
      expect(label).toHaveTextContent(labelText);
    });

    it('does not render label element when label is not provided', () => {
      render(<LoadingSpinner />);

      const label = screen.queryByTestId('loading-spinner-label');
      expect(label).not.toBeInTheDocument();
    });

    it('uses label text for screen reader text when label is provided', () => {
      const labelText = 'Fetching results...';
      render(<LoadingSpinner label={labelText} />);

      const srText = screen.getByTestId('loading-spinner-sr-text');
      expect(srText).toHaveTextContent(labelText);
    });
  });

  describe('Accessibility', () => {
    it('has role="status"', () => {
      render(<LoadingSpinner />);

      const spinner = screen.getByTestId('loading-spinner');
      expect(spinner).toHaveAttribute('role', 'status');
    });

    it('has aria-live="polite"', () => {
      render(<LoadingSpinner />);

      const spinner = screen.getByTestId('loading-spinner');
      expect(spinner).toHaveAttribute('aria-live', 'polite');
    });

    it('has aria-busy="true"', () => {
      render(<LoadingSpinner />);

      const spinner = screen.getByTestId('loading-spinner');
      expect(spinner).toHaveAttribute('aria-busy', 'true');
    });

    it('spinner icon is hidden from screen readers', () => {
      render(<LoadingSpinner />);

      const spinnerIcon = screen.getByTestId('loading-spinner-icon');
      expect(spinnerIcon).toHaveAttribute('aria-hidden', 'true');
    });

    it('screen reader text is visually hidden but accessible', () => {
      render(<LoadingSpinner />);

      const srText = screen.getByTestId('loading-spinner-sr-text');
      expect(srText).toBeInTheDocument();
      // The text should be accessible to screen readers
      expect(srText).toHaveTextContent('Loading...');
    });

    it('can be found by accessible role', () => {
      render(<LoadingSpinner />);

      const spinner = screen.getByRole('status');
      expect(spinner).toBeInTheDocument();
    });
  });

  describe('Custom Props', () => {
    it('applies custom className', () => {
      const customClass = 'my-custom-spinner';
      render(<LoadingSpinner className={customClass} />);

      const spinner = screen.getByTestId('loading-spinner');
      expect(spinner).toHaveClass(customClass);
    });

    it('supports custom data-testid', () => {
      const customTestId = 'my-spinner';
      render(<LoadingSpinner data-testid={customTestId} />);

      const spinner = screen.getByTestId(customTestId);
      expect(spinner).toBeInTheDocument();

      // Child elements should also use the custom testid prefix
      const spinnerIcon = screen.getByTestId(`${customTestId}-icon`);
      expect(spinnerIcon).toBeInTheDocument();

      const srText = screen.getByTestId(`${customTestId}-sr-text`);
      expect(srText).toBeInTheDocument();
    });

    it('supports custom data-testid with label', () => {
      const customTestId = 'custom-loader';
      render(<LoadingSpinner data-testid={customTestId} label="Loading..." />);

      const label = screen.getByTestId(`${customTestId}-label`);
      expect(label).toBeInTheDocument();
    });
  });

  describe('Combined Props', () => {
    it('renders correctly with all props', () => {
      render(
        <LoadingSpinner
          size="large"
          label="Please wait..."
          className="custom-class"
          data-testid="full-spinner"
        />
      );

      const spinner = screen.getByTestId('full-spinner');
      expect(spinner).toBeInTheDocument();
      expect(spinner).toHaveClass('custom-class');
      expect(spinner).toHaveAttribute('role', 'status');

      const spinnerIcon = screen.getByTestId('full-spinner-icon');
      // CSS Modules transform class names, so we check for partial match
      expect(spinnerIcon.className).toMatch(/large/);

      const label = screen.getByTestId('full-spinner-label');
      expect(label).toHaveTextContent('Please wait...');

      const srText = screen.getByTestId('full-spinner-sr-text');
      expect(srText).toHaveTextContent('Please wait...');
    });
  });

  describe('SVG Structure', () => {
    it('renders SVG with correct viewBox', () => {
      render(<LoadingSpinner />);

      const spinnerIcon = screen.getByTestId('loading-spinner-icon');
      const svg = spinnerIcon.querySelector('svg');
      expect(svg).toBeInTheDocument();
      expect(svg).toHaveAttribute('viewBox', '0 0 24 24');
    });

    it('renders track and arc circles', () => {
      render(<LoadingSpinner />);

      const spinnerIcon = screen.getByTestId('loading-spinner-icon');
      const circles = spinnerIcon.querySelectorAll('circle');
      expect(circles).toHaveLength(2);
    });
  });
});
