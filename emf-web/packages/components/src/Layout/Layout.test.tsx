import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PageLayout } from './PageLayout';
import { TwoColumnLayout } from './TwoColumnLayout';
import { ThreeColumnLayout } from './ThreeColumnLayout';

describe('PageLayout', () => {
  describe('Basic Rendering (Requirement 16.1)', () => {
    it('should render children within page wrapper', () => {
      render(
        <PageLayout>
          <div data-testid="content">Main Content</div>
        </PageLayout>
      );

      expect(screen.getByTestId('content')).toBeInTheDocument();
      expect(screen.getByText('Main Content')).toBeInTheDocument();
    });

    it('should render header when provided', () => {
      render(
        <PageLayout header={<div data-testid="header">Header Content</div>}>
          <div>Main Content</div>
        </PageLayout>
      );

      expect(screen.getByTestId('header')).toBeInTheDocument();
      expect(screen.getByText('Header Content')).toBeInTheDocument();
    });

    it('should render footer when provided', () => {
      render(
        <PageLayout footer={<div data-testid="footer">Footer Content</div>}>
          <div>Main Content</div>
        </PageLayout>
      );

      expect(screen.getByTestId('footer')).toBeInTheDocument();
      expect(screen.getByText('Footer Content')).toBeInTheDocument();
    });

    it('should render header and footer together', () => {
      render(
        <PageLayout
          header={<div>Header</div>}
          footer={<div>Footer</div>}
        >
          <div>Main</div>
        </PageLayout>
      );

      expect(screen.getByText('Header')).toBeInTheDocument();
      expect(screen.getByText('Main')).toBeInTheDocument();
      expect(screen.getByText('Footer')).toBeInTheDocument();
    });

    it('should not render header element when not provided', () => {
      render(
        <PageLayout>
          <div>Main Content</div>
        </PageLayout>
      );

      expect(screen.queryByRole('banner')).not.toBeInTheDocument();
    });

    it('should not render footer element when not provided', () => {
      render(
        <PageLayout>
          <div>Main Content</div>
        </PageLayout>
      );

      expect(screen.queryByRole('contentinfo')).not.toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have proper semantic roles', () => {
      render(
        <PageLayout
          header={<div>Header</div>}
          footer={<div>Footer</div>}
        >
          <div>Main</div>
        </PageLayout>
      );

      expect(screen.getByRole('banner')).toBeInTheDocument();
      expect(screen.getByRole('main')).toBeInTheDocument();
      expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    });
  });

  describe('Custom Styling (Requirement 16.5)', () => {
    it('should apply custom className', () => {
      render(
        <PageLayout className="custom-layout">
          <div>Content</div>
        </PageLayout>
      );

      expect(screen.getByTestId('emf-page-layout')).toHaveClass('custom-layout');
    });

    it('should apply custom style', () => {
      render(
        <PageLayout style={{ backgroundColor: 'red' }}>
          <div>Content</div>
        </PageLayout>
      );

      const layout = screen.getByTestId('emf-page-layout');
      expect(layout.style.backgroundColor).toBe('red');
    });

    it('should use custom testId', () => {
      render(
        <PageLayout testId="my-layout">
          <div>Content</div>
        </PageLayout>
      );

      expect(screen.getByTestId('my-layout')).toBeInTheDocument();
    });
  });
});

describe('TwoColumnLayout', () => {
  describe('Basic Rendering (Requirement 16.2)', () => {
    it('should render sidebar and main content', () => {
      render(
        <TwoColumnLayout
          sidebar={<div data-testid="sidebar">Sidebar</div>}
          main={<div data-testid="main">Main Content</div>}
        />
      );

      expect(screen.getByTestId('sidebar')).toBeInTheDocument();
      expect(screen.getByTestId('main')).toBeInTheDocument();
    });

    it('should render sidebar on left by default', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
        />
      );

      const layout = screen.getByTestId('emf-two-column-layout');
      expect(layout).toHaveClass('emf-two-column-layout--sidebar-left');
      expect(layout).toHaveAttribute('data-sidebar-position', 'left');
    });

    it('should render sidebar on right when specified', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
          sidebarPosition="right"
        />
      );

      const layout = screen.getByTestId('emf-two-column-layout');
      expect(layout).toHaveClass('emf-two-column-layout--sidebar-right');
      expect(layout).toHaveAttribute('data-sidebar-position', 'right');
    });
  });

  describe('Configurable Sidebar (Requirement 16.2)', () => {
    it('should apply custom sidebar width', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
          sidebarWidth="300px"
        />
      );

      const layout = screen.getByTestId('emf-two-column-layout');
      expect(layout).toHaveStyle({ '--sidebar-width': '300px' });
    });

    it('should use default sidebar width when not specified', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
        />
      );

      const layout = screen.getByTestId('emf-two-column-layout');
      expect(layout).toHaveStyle({ '--sidebar-width': '250px' });
    });
  });

  describe('Responsive Behavior (Requirement 16.4)', () => {
    it('should apply collapsible class when enabled', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
          collapsibleOnMobile={true}
        />
      );

      expect(screen.getByTestId('emf-two-column-layout')).toHaveClass('emf-two-column-layout--collapsible');
    });

    it('should not apply collapsible class when disabled', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
          collapsibleOnMobile={false}
        />
      );

      expect(screen.getByTestId('emf-two-column-layout')).not.toHaveClass('emf-two-column-layout--collapsible');
    });

    it('should apply custom breakpoints as CSS variables', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
          breakpoints={{ mobile: 600, tablet: 900, desktop: 1200 }}
        />
      );

      const layout = screen.getByTestId('emf-two-column-layout');
      expect(layout).toHaveStyle({ '--mobile-breakpoint': '600px' });
      expect(layout).toHaveStyle({ '--tablet-breakpoint': '900px' });
      expect(layout).toHaveStyle({ '--desktop-breakpoint': '1200px' });
    });
  });

  describe('Accessibility', () => {
    it('should have proper semantic roles', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
        />
      );

      expect(screen.getByRole('complementary')).toBeInTheDocument();
      expect(screen.getByRole('main')).toBeInTheDocument();
    });

    it('should have aria-label on sidebar', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
        />
      );

      expect(screen.getByRole('complementary')).toHaveAttribute('aria-label', 'Sidebar');
    });
  });

  describe('Custom Styling (Requirement 16.5)', () => {
    it('should apply custom className', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
          className="custom-two-column"
        />
      );

      expect(screen.getByTestId('emf-two-column-layout')).toHaveClass('custom-two-column');
    });

    it('should apply custom style', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
          style={{ gap: '20px' }}
        />
      );

      expect(screen.getByTestId('emf-two-column-layout')).toHaveStyle({ gap: '20px' });
    });

    it('should use custom testId', () => {
      render(
        <TwoColumnLayout
          sidebar={<div>Sidebar</div>}
          main={<div>Main</div>}
          testId="my-two-column"
        />
      );

      expect(screen.getByTestId('my-two-column')).toBeInTheDocument();
    });
  });
});

describe('ThreeColumnLayout', () => {
  describe('Basic Rendering (Requirement 16.3)', () => {
    it('should render left, main, and right content', () => {
      render(
        <ThreeColumnLayout
          left={<div data-testid="left">Left</div>}
          main={<div data-testid="main">Main</div>}
          right={<div data-testid="right">Right</div>}
        />
      );

      expect(screen.getByTestId('left')).toBeInTheDocument();
      expect(screen.getByTestId('main')).toBeInTheDocument();
      expect(screen.getByTestId('right')).toBeInTheDocument();
    });

    it('should render content in correct order', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left Content</div>}
          main={<div>Main Content</div>}
          right={<div>Right Content</div>}
        />
      );

      const layout = screen.getByTestId('emf-three-column-layout');
      const children = layout.children;
      
      expect(children[0]).toHaveClass('emf-three-column-layout__left');
      expect(children[1]).toHaveClass('emf-three-column-layout__main');
      expect(children[2]).toHaveClass('emf-three-column-layout__right');
    });
  });

  describe('Configurable Sidebars (Requirement 16.3)', () => {
    it('should apply custom left width', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
          leftWidth="250px"
        />
      );

      const layout = screen.getByTestId('emf-three-column-layout');
      expect(layout).toHaveStyle({ '--left-width': '250px' });
    });

    it('should apply custom right width', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
          rightWidth="300px"
        />
      );

      const layout = screen.getByTestId('emf-three-column-layout');
      expect(layout).toHaveStyle({ '--right-width': '300px' });
    });

    it('should use default widths when not specified', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
        />
      );

      const layout = screen.getByTestId('emf-three-column-layout');
      expect(layout).toHaveStyle({ '--left-width': '200px' });
      expect(layout).toHaveStyle({ '--right-width': '200px' });
    });
  });

  describe('Responsive Behavior (Requirement 16.4)', () => {
    it('should apply collapsible class when enabled', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
          collapsibleOnMobile={true}
        />
      );

      expect(screen.getByTestId('emf-three-column-layout')).toHaveClass('emf-three-column-layout--collapsible');
    });

    it('should not apply collapsible class when disabled', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
          collapsibleOnMobile={false}
        />
      );

      expect(screen.getByTestId('emf-three-column-layout')).not.toHaveClass('emf-three-column-layout--collapsible');
    });

    it('should apply custom breakpoints as CSS variables', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
          breakpoints={{ mobile: 500, tablet: 800, desktop: 1100 }}
        />
      );

      const layout = screen.getByTestId('emf-three-column-layout');
      expect(layout).toHaveStyle({ '--mobile-breakpoint': '500px' });
      expect(layout).toHaveStyle({ '--tablet-breakpoint': '800px' });
      expect(layout).toHaveStyle({ '--desktop-breakpoint': '1100px' });
    });
  });

  describe('Accessibility', () => {
    it('should have proper semantic roles', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
        />
      );

      const sidebars = screen.getAllByRole('complementary');
      expect(sidebars).toHaveLength(2);
      expect(screen.getByRole('main')).toBeInTheDocument();
    });

    it('should have aria-labels on sidebars', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
        />
      );

      expect(screen.getByLabelText('Left sidebar')).toBeInTheDocument();
      expect(screen.getByLabelText('Right sidebar')).toBeInTheDocument();
    });
  });

  describe('Custom Styling (Requirement 16.5)', () => {
    it('should apply custom className', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
          className="custom-three-column"
        />
      );

      expect(screen.getByTestId('emf-three-column-layout')).toHaveClass('custom-three-column');
    });

    it('should apply custom style', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
          style={{ padding: '10px' }}
        />
      );

      expect(screen.getByTestId('emf-three-column-layout')).toHaveStyle({ padding: '10px' });
    });

    it('should use custom testId', () => {
      render(
        <ThreeColumnLayout
          left={<div>Left</div>}
          main={<div>Main</div>}
          right={<div>Right</div>}
          testId="my-three-column"
        />
      );

      expect(screen.getByTestId('my-three-column')).toBeInTheDocument();
    });
  });
});
