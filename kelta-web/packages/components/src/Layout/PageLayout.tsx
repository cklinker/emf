import type { PageLayoutProps } from './types';

/**
 * PageLayout component for basic page structure.
 *
 * Features:
 * - Renders children within a standard page wrapper
 * - Supports optional header and footer
 * - Flexbox-based layout with proper semantic HTML
 * - Supports custom styling props
 *
 * @example
 * ```tsx
 * <PageLayout
 *   header={<Header />}
 *   footer={<Footer />}
 * >
 *   <main>Page content</main>
 * </PageLayout>
 * ```
 */
export function PageLayout({
  children,
  header,
  footer,
  className = '',
  style,
  testId = 'kelta-page-layout',
}: PageLayoutProps): JSX.Element {
  return (
    <div className={`kelta-page-layout ${className}`} style={style} data-testid={testId}>
      {header && (
        <header className="kelta-page-layout__header" role="banner">
          {header}
        </header>
      )}
      <main className="kelta-page-layout__main" role="main">
        {children}
      </main>
      {footer && (
        <footer className="kelta-page-layout__footer" role="contentinfo">
          {footer}
        </footer>
      )}
    </div>
  );
}
