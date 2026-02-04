/**
 * Toast Notification System Tests
 *
 * Tests for the Toast component, ToastProvider, and useToast hook.
 * Covers rendering, interactions, accessibility, and auto-dismiss behavior.
 *
 * Requirements tested:
 * - 18.1: Display appropriate error messages when API requests fail
 * - 18.3: Display success messages after successful operations
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import {
  Toast,
  ToastProvider,
  useToast,
  DEFAULT_DURATION,
} from './Toast';

// Test component that uses the useToast hook
function TestComponent() {
  const { showToast, hideAllToasts, toasts } = useToast();

  return (
    <div>
      <button
        data-testid="show-success"
        onClick={() => showToast('Success message', 'success')}
      >
        Show Success
      </button>
      <button
        data-testid="show-error"
        onClick={() => showToast('Error message', 'error')}
      >
        Show Error
      </button>
      <button
        data-testid="show-warning"
        onClick={() => showToast('Warning message', 'warning')}
      >
        Show Warning
      </button>
      <button
        data-testid="show-info"
        onClick={() => showToast('Info message', 'info')}
      >
        Show Info
      </button>
      <button
        data-testid="show-custom-duration"
        onClick={() => showToast('Custom duration', 'info', 1000)}
      >
        Show Custom Duration
      </button>
      <button
        data-testid="show-no-auto-dismiss"
        onClick={() => showToast('No auto dismiss', 'info', 0)}
      >
        Show No Auto Dismiss
      </button>
      <button data-testid="hide-all" onClick={hideAllToasts}>
        Hide All
      </button>
      <div data-testid="toast-count">{toasts.length}</div>
    </div>
  );
}

describe('Toast Component', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('Rendering', () => {
    it('should render toast with message', () => {
      const onClose = vi.fn();
      render(<Toast message="Test message" type="success" onClose={onClose} />);

      expect(screen.getByTestId('toast-message')).toHaveTextContent('Test message');
    });

    it('should render success toast with correct styling', () => {
      const onClose = vi.fn();
      render(<Toast message="Success!" type="success" onClose={onClose} />);

      expect(screen.getByTestId('toast-success')).toBeInTheDocument();
    });

    it('should render error toast with correct styling', () => {
      const onClose = vi.fn();
      render(<Toast message="Error!" type="error" onClose={onClose} />);

      expect(screen.getByTestId('toast-error')).toBeInTheDocument();
    });

    it('should render warning toast with correct styling', () => {
      const onClose = vi.fn();
      render(<Toast message="Warning!" type="warning" onClose={onClose} />);

      expect(screen.getByTestId('toast-warning')).toBeInTheDocument();
    });

    it('should render info toast with correct styling', () => {
      const onClose = vi.fn();
      render(<Toast message="Info!" type="info" onClose={onClose} />);

      expect(screen.getByTestId('toast-info')).toBeInTheDocument();
    });

    it('should render icon for each toast type', () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" onClose={onClose} />);

      expect(screen.getByTestId('toast-icon')).toBeInTheDocument();
    });

    it('should render close button', () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" onClose={onClose} />);

      expect(screen.getByTestId('toast-close-button')).toBeInTheDocument();
    });
  });

  describe('Close Behavior', () => {
    it('should call onClose when close button is clicked', async () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" onClose={onClose} />);

      fireEvent.click(screen.getByTestId('toast-close-button'));

      // Wait for exit animation
      act(() => {
        vi.advanceTimersByTime(200);
      });

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should call onClose when Escape key is pressed', async () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" onClose={onClose} />);

      fireEvent.keyDown(screen.getByTestId('toast-success'), { key: 'Escape' });

      // Wait for exit animation
      act(() => {
        vi.advanceTimersByTime(200);
      });

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should auto-dismiss after default duration', () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" onClose={onClose} />);

      // Advance time to just before auto-dismiss
      act(() => {
        vi.advanceTimersByTime(DEFAULT_DURATION - 1);
      });
      expect(onClose).not.toHaveBeenCalled();

      // Advance time past auto-dismiss + animation
      act(() => {
        vi.advanceTimersByTime(1 + 200);
      });
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should auto-dismiss after custom duration', () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" duration={1000} onClose={onClose} />);

      // Advance time to just before auto-dismiss
      act(() => {
        vi.advanceTimersByTime(999);
      });
      expect(onClose).not.toHaveBeenCalled();

      // Advance time past auto-dismiss + animation
      act(() => {
        vi.advanceTimersByTime(1 + 200);
      });
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('should not auto-dismiss when duration is 0', () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" duration={0} onClose={onClose} />);

      // Advance time significantly
      act(() => {
        vi.advanceTimersByTime(10000);
      });

      expect(onClose).not.toHaveBeenCalled();
    });
  });

  describe('Accessibility', () => {
    it('should have role="alert" for error toasts', () => {
      const onClose = vi.fn();
      render(<Toast message="Error!" type="error" onClose={onClose} />);

      expect(screen.getByTestId('toast-error')).toHaveAttribute('role', 'alert');
    });

    it('should have role="alert" for warning toasts', () => {
      const onClose = vi.fn();
      render(<Toast message="Warning!" type="warning" onClose={onClose} />);

      expect(screen.getByTestId('toast-warning')).toHaveAttribute('role', 'alert');
    });

    it('should have role="status" for success toasts', () => {
      const onClose = vi.fn();
      render(<Toast message="Success!" type="success" onClose={onClose} />);

      expect(screen.getByTestId('toast-success')).toHaveAttribute('role', 'status');
    });

    it('should have role="status" for info toasts', () => {
      const onClose = vi.fn();
      render(<Toast message="Info!" type="info" onClose={onClose} />);

      expect(screen.getByTestId('toast-info')).toHaveAttribute('role', 'status');
    });

    it('should have aria-live="assertive" for error toasts', () => {
      const onClose = vi.fn();
      render(<Toast message="Error!" type="error" onClose={onClose} />);

      expect(screen.getByTestId('toast-error')).toHaveAttribute('aria-live', 'assertive');
    });

    it('should have aria-live="polite" for success toasts', () => {
      const onClose = vi.fn();
      render(<Toast message="Success!" type="success" onClose={onClose} />);

      expect(screen.getByTestId('toast-success')).toHaveAttribute('aria-live', 'polite');
    });

    it('should have aria-atomic="true"', () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" onClose={onClose} />);

      expect(screen.getByTestId('toast-success')).toHaveAttribute('aria-atomic', 'true');
    });

    it('should have accessible close button with aria-label', () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" onClose={onClose} />);

      expect(screen.getByTestId('toast-close-button')).toHaveAttribute(
        'aria-label',
        'Close notification'
      );
    });

    it('should have aria-hidden on icon', () => {
      const onClose = vi.fn();
      render(<Toast message="Test" type="success" onClose={onClose} />);

      expect(screen.getByTestId('toast-icon')).toHaveAttribute('aria-hidden', 'true');
    });
  });
});

describe('ToastProvider', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('Rendering', () => {
    it('should render children', () => {
      render(
        <ToastProvider>
          <div data-testid="child">Child content</div>
        </ToastProvider>
      );

      expect(screen.getByTestId('child')).toBeInTheDocument();
    });

    it('should not render toast container when no toasts', () => {
      render(
        <ToastProvider>
          <div>Content</div>
        </ToastProvider>
      );

      expect(screen.queryByTestId('toast-container')).not.toBeInTheDocument();
    });

    it('should render toast container when toasts are shown', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));

      expect(screen.getByTestId('toast-container')).toBeInTheDocument();
    });
  });

  describe('showToast', () => {
    it('should show success toast', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));

      expect(screen.getByTestId('toast-success')).toBeInTheDocument();
      expect(screen.getByTestId('toast-message')).toHaveTextContent('Success message');
    });

    it('should show error toast', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-error'));

      expect(screen.getByTestId('toast-error')).toBeInTheDocument();
      expect(screen.getByTestId('toast-message')).toHaveTextContent('Error message');
    });

    it('should show warning toast', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-warning'));

      expect(screen.getByTestId('toast-warning')).toBeInTheDocument();
      expect(screen.getByTestId('toast-message')).toHaveTextContent('Warning message');
    });

    it('should show info toast', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-info'));

      expect(screen.getByTestId('toast-info')).toBeInTheDocument();
      expect(screen.getByTestId('toast-message')).toHaveTextContent('Info message');
    });

    it('should show multiple toasts simultaneously', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));
      fireEvent.click(screen.getByTestId('show-error'));
      fireEvent.click(screen.getByTestId('show-info'));

      expect(screen.getByTestId('toast-count')).toHaveTextContent('3');
    });

    it('should limit toasts to maxToasts', () => {
      render(
        <ToastProvider maxToasts={2}>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));
      fireEvent.click(screen.getByTestId('show-error'));
      fireEvent.click(screen.getByTestId('show-info'));

      expect(screen.getByTestId('toast-count')).toHaveTextContent('2');
    });

    it('should return toast ID', () => {
      let toastId: string | undefined;

      function TestWithId() {
        const { showToast } = useToast();
        return (
          <button
            onClick={() => {
              toastId = showToast('Test', 'success');
            }}
          >
            Show
          </button>
        );
      }

      render(
        <ToastProvider>
          <TestWithId />
        </ToastProvider>
      );

      fireEvent.click(screen.getByText('Show'));

      expect(toastId).toBeDefined();
      expect(typeof toastId).toBe('string');
      expect(toastId).toMatch(/^toast-/);
    });
  });

  describe('hideToast', () => {
    it('should hide specific toast by ID', () => {
      let toastId: string | undefined;

      function TestWithHide() {
        const { showToast, hideToast, toasts } = useToast();
        return (
          <div>
            <button
              data-testid="show"
              onClick={() => {
                toastId = showToast('Test', 'success');
              }}
            >
              Show
            </button>
            <button
              data-testid="hide"
              onClick={() => {
                if (toastId) hideToast(toastId);
              }}
            >
              Hide
            </button>
            <div data-testid="count">{toasts.length}</div>
          </div>
        );
      }

      render(
        <ToastProvider>
          <TestWithHide />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show'));
      expect(screen.getByTestId('count')).toHaveTextContent('1');

      fireEvent.click(screen.getByTestId('hide'));
      expect(screen.getByTestId('count')).toHaveTextContent('0');
    });
  });

  describe('hideAllToasts', () => {
    it('should hide all toasts', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));
      fireEvent.click(screen.getByTestId('show-error'));
      fireEvent.click(screen.getByTestId('show-info'));

      expect(screen.getByTestId('toast-count')).toHaveTextContent('3');

      fireEvent.click(screen.getByTestId('hide-all'));

      expect(screen.getByTestId('toast-count')).toHaveTextContent('0');
    });
  });

  describe('Auto-dismiss', () => {
    it('should auto-dismiss toast after default duration', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));
      expect(screen.getByTestId('toast-count')).toHaveTextContent('1');

      // Advance time past default duration + animation
      act(() => {
        vi.advanceTimersByTime(DEFAULT_DURATION + 200);
      });

      expect(screen.getByTestId('toast-count')).toHaveTextContent('0');
    });

    it('should auto-dismiss toast after custom duration', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-custom-duration'));
      expect(screen.getByTestId('toast-count')).toHaveTextContent('1');

      // Advance time past custom duration + animation
      act(() => {
        vi.advanceTimersByTime(1000 + 200);
      });

      expect(screen.getByTestId('toast-count')).toHaveTextContent('0');
    });

    it('should not auto-dismiss when duration is 0', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-no-auto-dismiss'));
      expect(screen.getByTestId('toast-count')).toHaveTextContent('1');

      // Advance time significantly
      act(() => {
        vi.advanceTimersByTime(10000);
      });

      expect(screen.getByTestId('toast-count')).toHaveTextContent('1');
    });

    it('should use custom default duration from provider', () => {
      render(
        <ToastProvider defaultDuration={2000}>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));
      expect(screen.getByTestId('toast-count')).toHaveTextContent('1');

      // Advance time to just before custom default duration
      act(() => {
        vi.advanceTimersByTime(1999);
      });
      expect(screen.getByTestId('toast-count')).toHaveTextContent('1');

      // Advance time past custom default duration + animation
      act(() => {
        vi.advanceTimersByTime(1 + 200);
      });
      expect(screen.getByTestId('toast-count')).toHaveTextContent('0');
    });
  });

  describe('Manual Close', () => {
    it('should close toast when close button is clicked', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));
      expect(screen.getByTestId('toast-count')).toHaveTextContent('1');

      fireEvent.click(screen.getByTestId('toast-close-button'));

      // Wait for exit animation
      act(() => {
        vi.advanceTimersByTime(200);
      });

      expect(screen.getByTestId('toast-count')).toHaveTextContent('0');
    });
  });

  describe('Position', () => {
    it('should render container with default position (top-right)', () => {
      render(
        <ToastProvider>
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));

      const container = screen.getByTestId('toast-container');
      // CSS Modules transform class names, so we check for partial match
      expect(container.className).toMatch(/topright/);
    });

    it('should render container with custom position', () => {
      render(
        <ToastProvider position="bottom-left">
          <TestComponent />
        </ToastProvider>
      );

      fireEvent.click(screen.getByTestId('show-success'));

      const container = screen.getByTestId('toast-container');
      // CSS Modules transform class names, so we check for partial match
      expect(container.className).toMatch(/bottomleft/);
    });
  });
});

describe('useToast', () => {
  it('should throw error when used outside ToastProvider', () => {
    // Suppress console.error for this test
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    function TestOutsideProvider() {
      useToast();
      return null;
    }

    expect(() => render(<TestOutsideProvider />)).toThrow(
      'useToast must be used within a ToastProvider'
    );

    consoleSpy.mockRestore();
  });

  it('should return context value when used inside ToastProvider', () => {
    const contextValueRef = { current: undefined as ReturnType<typeof useToast> | undefined };

    function TestInsideProvider() {
      const value = useToast();
      // Use ref pattern to capture value without triggering lint warning
      React.useEffect(() => {
        contextValueRef.current = value;
      }, [value]);
      return <div data-testid="has-context">{value ? 'yes' : 'no'}</div>;
    }

    render(
      <ToastProvider>
        <TestInsideProvider />
      </ToastProvider>
    );

    // Verify the component rendered with context
    expect(screen.getByTestId('has-context')).toHaveTextContent('yes');
  });
});

describe('Toast Container Accessibility', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should have aria-label on container', () => {
    render(
      <ToastProvider>
        <TestComponent />
      </ToastProvider>
    );

    fireEvent.click(screen.getByTestId('show-success'));

    expect(screen.getByTestId('toast-container')).toHaveAttribute(
      'aria-label',
      'Notifications'
    );
  });
});

describe('Toast Integration', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should handle rapid toast creation', () => {
    render(
      <ToastProvider maxToasts={5}>
        <TestComponent />
      </ToastProvider>
    );

    // Rapidly create toasts
    for (let i = 0; i < 10; i++) {
      fireEvent.click(screen.getByTestId('show-success'));
    }

    // Should be limited to maxToasts
    expect(screen.getByTestId('toast-count')).toHaveTextContent('5');
  });

  it('should handle toast creation and dismissal in sequence', () => {
    render(
      <ToastProvider defaultDuration={1000}>
        <TestComponent />
      </ToastProvider>
    );

    // Create first toast
    fireEvent.click(screen.getByTestId('show-success'));
    expect(screen.getByTestId('toast-count')).toHaveTextContent('1');

    // Wait for it to dismiss
    act(() => {
      vi.advanceTimersByTime(1200);
    });
    expect(screen.getByTestId('toast-count')).toHaveTextContent('0');

    // Create second toast
    fireEvent.click(screen.getByTestId('show-error'));
    expect(screen.getByTestId('toast-count')).toHaveTextContent('1');

    // Wait for it to dismiss
    act(() => {
      vi.advanceTimersByTime(1200);
    });
    expect(screen.getByTestId('toast-count')).toHaveTextContent('0');
  });
});
