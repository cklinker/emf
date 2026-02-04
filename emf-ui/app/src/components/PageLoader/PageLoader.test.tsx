/**
 * PageLoader Component Tests
 *
 * Tests for the PageLoader, Skeleton, and ContentLoader components covering:
 * - PageLoader rendering and variants
 * - Skeleton variants (text, card, table, form, header, list)
 * - ContentLoader loading state handling
 * - Accessibility attributes
 * - Custom props support
 */

import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PageLoader, Skeleton, ContentLoader } from './PageLoader';
import type { SkeletonVariant } from './PageLoader';

describe('PageLoader', () => {
  describe('Rendering', () => {
    it('renders with default props', () => {
      render(<PageLoader />);

      const loader = screen.getByTestId('page-loader');
      expect(loader).toBeInTheDocument();
    });

    it('renders the spinner', () => {
      render(<PageLoader />);

      const spinner = screen.getByTestId('page-loader-spinner');
      expect(spinner).toBeInTheDocument();
    });

    it('displays default loading message', () => {
      render(<PageLoader />);

      // The message appears in both the visible label and screen reader text
      const messages = screen.getAllByText('Loading...');
      expect(messages.length).toBeGreaterThanOrEqual(1);
    });

    it('displays custom loading message', () => {
      render(<PageLoader message="Loading dashboard..." />);

      // The message appears in both the visible label and screen reader text
      const messages = screen.getAllByText('Loading dashboard...');
      expect(messages.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('Variants', () => {
    it('renders as inline by default', () => {
      render(<PageLoader />);

      const loader = screen.getByTestId('page-loader');
      expect(loader.className).toMatch(/inline/);
    });

    it('renders as full page when fullPage is true', () => {
      render(<PageLoader fullPage />);

      const loader = screen.getByTestId('page-loader');
      expect(loader.className).toMatch(/fullPage/);
    });
  });

  describe('Spinner Size', () => {
    it('uses large size by default', () => {
      render(<PageLoader />);

      const spinner = screen.getByTestId('page-loader-spinner');
      expect(spinner).toBeInTheDocument();
    });

    it('supports small size', () => {
      render(<PageLoader size="small" />);

      const spinner = screen.getByTestId('page-loader-spinner');
      expect(spinner).toBeInTheDocument();
    });

    it('supports medium size', () => {
      render(<PageLoader size="medium" />);

      const spinner = screen.getByTestId('page-loader-spinner');
      expect(spinner).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('has role="status"', () => {
      render(<PageLoader />);

      const loader = screen.getByTestId('page-loader');
      expect(loader).toHaveAttribute('role', 'status');
    });

    it('has aria-live="polite"', () => {
      render(<PageLoader />);

      const loader = screen.getByTestId('page-loader');
      expect(loader).toHaveAttribute('aria-live', 'polite');
    });

    it('has aria-busy="true"', () => {
      render(<PageLoader />);

      const loader = screen.getByTestId('page-loader');
      expect(loader).toHaveAttribute('aria-busy', 'true');
    });
  });

  describe('Custom Props', () => {
    it('applies custom className', () => {
      render(<PageLoader className="custom-loader" />);

      const loader = screen.getByTestId('page-loader');
      expect(loader).toHaveClass('custom-loader');
    });

    it('supports custom data-testid', () => {
      render(<PageLoader data-testid="my-loader" />);

      expect(screen.getByTestId('my-loader')).toBeInTheDocument();
      expect(screen.getByTestId('my-loader-spinner')).toBeInTheDocument();
    });
  });
});

describe('Skeleton', () => {
  describe('Rendering', () => {
    it('renders with default props', () => {
      render(<Skeleton />);

      const container = screen.getByTestId('skeleton-container');
      expect(container).toBeInTheDocument();
    });

    it('renders skeleton item', () => {
      render(<Skeleton />);

      const skeleton = screen.getByTestId('skeleton');
      expect(skeleton).toBeInTheDocument();
    });
  });

  describe('Variants', () => {
    const variants: SkeletonVariant[] = ['text', 'card', 'table', 'form', 'header', 'list'];

    variants.forEach((variant) => {
      it(`renders ${variant} variant`, () => {
        render(<Skeleton variant={variant} />);

        const skeleton = screen.getByTestId('skeleton');
        expect(skeleton).toBeInTheDocument();
      });
    });

    it('defaults to text variant', () => {
      render(<Skeleton />);

      const skeleton = screen.getByTestId('skeleton');
      expect(skeleton.className).toMatch(/skeletonText/);
    });
  });

  describe('Count', () => {
    it('renders single item by default', () => {
      render(<Skeleton variant="text" />);

      const skeleton = screen.getByTestId('skeleton');
      expect(skeleton).toBeInTheDocument();
    });

    it('renders multiple items when count is specified', () => {
      render(<Skeleton variant="text" count={3} />);

      expect(screen.getByTestId('skeleton-item-0')).toBeInTheDocument();
      expect(screen.getByTestId('skeleton-item-1')).toBeInTheDocument();
      expect(screen.getByTestId('skeleton-item-2')).toBeInTheDocument();
    });

    it('renders correct number of table rows', () => {
      render(<Skeleton variant="table" count={5} />);

      for (let i = 0; i < 5; i++) {
        expect(screen.getByTestId(`skeleton-item-${i}`)).toBeInTheDocument();
      }
    });

    it('renders correct number of list items', () => {
      render(<Skeleton variant="list" count={4} />);

      for (let i = 0; i < 4; i++) {
        expect(screen.getByTestId(`skeleton-item-${i}`)).toBeInTheDocument();
      }
    });
  });

  describe('Custom Dimensions', () => {
    it('applies custom width', () => {
      render(<Skeleton width="200px" />);

      const skeleton = screen.getByTestId('skeleton');
      expect(skeleton).toHaveStyle({ width: '200px' });
    });

    it('applies custom height', () => {
      render(<Skeleton height="50px" />);

      const skeleton = screen.getByTestId('skeleton');
      expect(skeleton).toHaveStyle({ height: '50px' });
    });

    it('applies both width and height', () => {
      render(<Skeleton width="300px" height="100px" />);

      const skeleton = screen.getByTestId('skeleton');
      expect(skeleton).toHaveStyle({ width: '300px', height: '100px' });
    });
  });

  describe('Accessibility', () => {
    it('has role="status" on container', () => {
      render(<Skeleton />);

      const container = screen.getByTestId('skeleton-container');
      expect(container).toHaveAttribute('role', 'status');
    });

    it('has aria-label on container', () => {
      render(<Skeleton />);

      const container = screen.getByTestId('skeleton-container');
      expect(container).toHaveAttribute('aria-label', 'Loading content');
    });

    it('skeleton items are hidden from screen readers', () => {
      render(<Skeleton />);

      const skeleton = screen.getByTestId('skeleton');
      expect(skeleton).toHaveAttribute('aria-hidden', 'true');
    });

    it('provides screen reader text', () => {
      render(<Skeleton />);

      expect(screen.getByText('Loading...')).toBeInTheDocument();
    });
  });

  describe('Custom Props', () => {
    it('applies custom className', () => {
      render(<Skeleton className="custom-skeleton" />);

      const container = screen.getByTestId('skeleton-container');
      expect(container).toHaveClass('custom-skeleton');
    });

    it('supports custom data-testid', () => {
      render(<Skeleton data-testid="my-skeleton" />);

      expect(screen.getByTestId('my-skeleton-container')).toBeInTheDocument();
      expect(screen.getByTestId('my-skeleton')).toBeInTheDocument();
    });
  });
});

describe('ContentLoader', () => {
  describe('Loading State', () => {
    it('shows skeleton when loading', () => {
      render(
        <ContentLoader isLoading={true}>
          <div data-testid="content">Content</div>
        </ContentLoader>
      );

      expect(screen.getByTestId('content-loader-skeleton-container')).toBeInTheDocument();
      expect(screen.queryByTestId('content')).not.toBeInTheDocument();
    });

    it('shows content when not loading', () => {
      render(
        <ContentLoader isLoading={false}>
          <div data-testid="content">Content</div>
        </ContentLoader>
      );

      expect(screen.getByTestId('content')).toBeInTheDocument();
      expect(screen.queryByTestId('content-loader-skeleton-container')).not.toBeInTheDocument();
    });
  });

  describe('Skeleton Configuration', () => {
    it('uses text skeleton by default', () => {
      render(
        <ContentLoader isLoading={true}>
          <div>Content</div>
        </ContentLoader>
      );

      // With default count of 3, the first item has testid content-loader-skeleton-item-0
      const skeleton = screen.getByTestId('content-loader-skeleton-item-0');
      expect(skeleton.className).toMatch(/skeletonText/);
    });

    it('uses specified skeleton variant', () => {
      render(
        <ContentLoader isLoading={true} skeleton="card">
          <div>Content</div>
        </ContentLoader>
      );

      const skeleton = screen.getByTestId('content-loader-skeleton-item-0');
      expect(skeleton.className).toMatch(/skeletonCard/);
    });

    it('renders specified number of skeleton items', () => {
      render(
        <ContentLoader isLoading={true} skeleton="list" skeletonCount={5}>
          <div>Content</div>
        </ContentLoader>
      );

      for (let i = 0; i < 5; i++) {
        expect(screen.getByTestId(`content-loader-skeleton-item-${i}`)).toBeInTheDocument();
      }
    });
  });

  describe('Accessibility', () => {
    it('has aria-busy="true" when loading', () => {
      render(
        <ContentLoader isLoading={true}>
          <div>Content</div>
        </ContentLoader>
      );

      const loader = screen.getByTestId('content-loader');
      expect(loader).toHaveAttribute('aria-busy', 'true');
    });

    it('has aria-busy="false" when not loading', () => {
      render(
        <ContentLoader isLoading={false}>
          <div>Content</div>
        </ContentLoader>
      );

      const loader = screen.getByTestId('content-loader');
      expect(loader).toHaveAttribute('aria-busy', 'false');
    });

    it('provides screen reader loading message', () => {
      render(
        <ContentLoader isLoading={true} loadingMessage="Loading items...">
          <div>Content</div>
        </ContentLoader>
      );

      expect(screen.getByText('Loading items...')).toBeInTheDocument();
    });
  });

  describe('Custom Props', () => {
    it('applies custom className', () => {
      render(
        <ContentLoader isLoading={false} className="custom-content-loader">
          <div>Content</div>
        </ContentLoader>
      );

      const loader = screen.getByTestId('content-loader');
      expect(loader).toHaveClass('custom-content-loader');
    });

    it('supports custom data-testid', () => {
      render(
        <ContentLoader isLoading={true} data-testid="my-content-loader">
          <div>Content</div>
        </ContentLoader>
      );

      expect(screen.getByTestId('my-content-loader')).toBeInTheDocument();
      expect(screen.getByTestId('my-content-loader-skeleton-container')).toBeInTheDocument();
    });
  });

  describe('Transition Between States', () => {
    it('transitions from loading to content', () => {
      const { rerender } = render(
        <ContentLoader isLoading={true}>
          <div data-testid="content">Content</div>
        </ContentLoader>
      );

      expect(screen.queryByTestId('content')).not.toBeInTheDocument();

      rerender(
        <ContentLoader isLoading={false}>
          <div data-testid="content">Content</div>
        </ContentLoader>
      );

      expect(screen.getByTestId('content')).toBeInTheDocument();
    });

    it('transitions from content to loading', () => {
      const { rerender } = render(
        <ContentLoader isLoading={false}>
          <div data-testid="content">Content</div>
        </ContentLoader>
      );

      expect(screen.getByTestId('content')).toBeInTheDocument();

      rerender(
        <ContentLoader isLoading={true}>
          <div data-testid="content">Content</div>
        </ContentLoader>
      );

      expect(screen.queryByTestId('content')).not.toBeInTheDocument();
      expect(screen.getByTestId('content-loader-skeleton-container')).toBeInTheDocument();
    });
  });
});
