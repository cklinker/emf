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
  testId = 'emf-page-layout',
}: PageLayoutProps): JSX.Element {
  return (
    <div 
      className={`emf-page-layout ${className}`} 
      style={style}
      data-testid={testId}
    >
      {header && (
        <header 
          className="emf-page-layout__header"
          role="banner"
        >
          {header}
        </header>
      )}
      <main 
        className="emf-page-layout__main"
        role="main"
      >
        {children}
      </main>
      {footer && (
        <footer 
          className="emf-page-layout__footer"
          role="contentinfo"
        >
          {footer}
        </footer>
      )}
    </div>
  );
}
