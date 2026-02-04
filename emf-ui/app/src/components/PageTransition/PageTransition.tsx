/**
 * PageTransition Component
 *
 * Wraps page content with fade/slide animations for smooth page transitions.
 * Respects the prefers-reduced-motion media query for accessibility.
 *
 * Requirements:
 * - 18.4: Display loading indicators during async operations
 * - 14.7: Support reduced motion preferences from the operating system
 *
 * Features:
 * - Fade-in animation on mount
 * - Optional slide-up animation
 * - Respects prefers-reduced-motion preference
 * - CSS transitions for smooth page changes
 * - Configurable animation duration
 */

import React, { useEffect, useState, useRef } from 'react';
import styles from './PageTransition.module.css';

/**
 * Animation type options
 */
export type TransitionType = 'fade' | 'fade-slide' | 'none';

/**
 * Props for the PageTransition component
 */
export interface PageTransitionProps {
  /** Content to wrap with transition animation */
  children: React.ReactNode;
  /** Type of transition animation */
  type?: TransitionType;
  /** Duration of the transition in milliseconds */
  duration?: number;
  /** Optional custom class name */
  className?: string;
  /** Optional test ID for testing purposes */
  'data-testid'?: string;
}

/**
 * Hook to detect if user prefers reduced motion
 */
function usePrefersReducedMotion(): boolean {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(() => {
    // Check if window is available (SSR safety)
    if (typeof window === 'undefined') {
      return false;
    }
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    return mediaQuery.matches;
  });

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    
    const handleChange = (event: MediaQueryListEvent) => {
      setPrefersReducedMotion(event.matches);
    };

    // Modern browsers
    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', handleChange);
      return () => mediaQuery.removeEventListener('change', handleChange);
    }
    // Legacy browsers
    mediaQuery.addListener(handleChange);
    return () => mediaQuery.removeListener(handleChange);
  }, []);

  return prefersReducedMotion;
}

/**
 * PageTransition Component
 *
 * Provides smooth page transition animations while respecting accessibility preferences.
 *
 * @example
 * ```tsx
 * // Basic fade transition
 * <PageTransition>
 *   <MyPageContent />
 * </PageTransition>
 *
 * // Fade with slide animation
 * <PageTransition type="fade-slide">
 *   <MyPageContent />
 * </PageTransition>
 *
 * // No animation
 * <PageTransition type="none">
 *   <MyPageContent />
 * </PageTransition>
 * ```
 */
export function PageTransition({
  children,
  type = 'fade',
  duration = 200,
  className,
  'data-testid': testId = 'page-transition',
}: PageTransitionProps): React.ReactElement {
  const [isVisible, setIsVisible] = useState(false);
  const prefersReducedMotion = usePrefersReducedMotion();
  const containerRef = useRef<HTMLDivElement>(null);

  // Trigger animation on mount
  useEffect(() => {
    // Use requestAnimationFrame to ensure the initial state is rendered first
    const frameId = requestAnimationFrame(() => {
      setIsVisible(true);
    });

    return () => {
      cancelAnimationFrame(frameId);
    };
  }, []);

  // Determine effective transition type based on reduced motion preference
  const effectiveType = prefersReducedMotion ? 'none' : type;

  // Build class names
  const containerClasses = [
    styles.container,
    styles[effectiveType.replace('-', '')], // 'fade-slide' -> 'fadeslide'
    isVisible ? styles.visible : styles.hidden,
    className,
  ].filter(Boolean).join(' ');

  // Set CSS custom property for duration
  const style: React.CSSProperties = {
    '--transition-duration': `${duration}ms`,
  } as React.CSSProperties;

  return (
    <div
      ref={containerRef}
      className={containerClasses}
      style={style}
      data-testid={testId}
      data-transition-type={effectiveType}
      data-reduced-motion={prefersReducedMotion ? 'true' : 'false'}
    >
      {children}
    </div>
  );
}

// Export the hook for external use
export { usePrefersReducedMotion };

// Export default for convenience
export default PageTransition;
