/**
 * useKeyboardShortcuts Hook Tests
 * 
 * Tests for keyboard shortcut handling functionality.
 * Validates requirement 14.2: All interactive elements are keyboard accessible
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { 
  useKeyboardShortcuts, 
  useEscapeKey, 
  formatShortcut,
  type KeyboardShortcut 
} from './useKeyboardShortcuts';

describe('useKeyboardShortcuts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Basic Functionality', () => {
    it('should call handler when matching key is pressed', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k' });
        document.dispatchEvent(event);
      });

      expect(handler).toHaveBeenCalledTimes(1);
    });

    it('should not call handler when different key is pressed', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'j' });
        document.dispatchEvent(event);
      });

      expect(handler).not.toHaveBeenCalled();
    });

    it('should handle case-insensitive key matching', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'K', handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k' });
        document.dispatchEvent(event);
      });

      expect(handler).toHaveBeenCalledTimes(1);
    });

    it('should handle special keys like Escape', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'Escape', handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'Escape' });
        document.dispatchEvent(event);
      });

      expect(handler).toHaveBeenCalledTimes(1);
    });
  });

  describe('Modifier Keys', () => {
    it('should require Ctrl modifier when specified', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', modifiers: { ctrl: true }, handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      // Without Ctrl - should not trigger
      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: false });
        document.dispatchEvent(event);
      });
      expect(handler).not.toHaveBeenCalled();

      // With Ctrl - should trigger
      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true });
        document.dispatchEvent(event);
      });
      expect(handler).toHaveBeenCalledTimes(1);
    });

    it('should require Alt modifier when specified', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', modifiers: { alt: true }, handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k', altKey: true });
        document.dispatchEvent(event);
      });

      expect(handler).toHaveBeenCalledTimes(1);
    });

    it('should require Shift modifier when specified', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', modifiers: { shift: true }, handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k', shiftKey: true });
        document.dispatchEvent(event);
      });

      expect(handler).toHaveBeenCalledTimes(1);
    });

    it('should require Meta modifier when specified', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', modifiers: { meta: true }, handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k', metaKey: true });
        document.dispatchEvent(event);
      });

      expect(handler).toHaveBeenCalledTimes(1);
    });

    it('should require multiple modifiers when specified', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', modifiers: { ctrl: true, shift: true }, handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      // Only Ctrl - should not trigger
      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, shiftKey: false });
        document.dispatchEvent(event);
      });
      expect(handler).not.toHaveBeenCalled();

      // Both Ctrl and Shift - should trigger
      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, shiftKey: true });
        document.dispatchEvent(event);
      });
      expect(handler).toHaveBeenCalledTimes(1);
    });

    it('should not trigger when extra modifiers are pressed', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', modifiers: { ctrl: true }, handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      // Ctrl + Shift when only Ctrl is expected - should not trigger
      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, shiftKey: true });
        document.dispatchEvent(event);
      });

      expect(handler).not.toHaveBeenCalled();
    });
  });

  describe('Input Element Handling', () => {
    it('should not trigger shortcuts when focused on input by default', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      // Create an input element and focus it
      const input = document.createElement('input');
      document.body.appendChild(input);
      input.focus();

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k' });
        Object.defineProperty(event, 'target', { value: input });
        document.dispatchEvent(event);
      });

      expect(handler).not.toHaveBeenCalled();

      // Cleanup
      document.body.removeChild(input);
    });

    it('should trigger shortcuts in input when allowInInput is true', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'Escape', handler, allowInInput: true },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      const input = document.createElement('input');
      document.body.appendChild(input);
      input.focus();

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'Escape' });
        Object.defineProperty(event, 'target', { value: input });
        document.dispatchEvent(event);
      });

      expect(handler).toHaveBeenCalledTimes(1);

      document.body.removeChild(input);
    });

    it('should not trigger shortcuts when focused on textarea', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      const textarea = document.createElement('textarea');
      document.body.appendChild(textarea);
      textarea.focus();

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k' });
        Object.defineProperty(event, 'target', { value: textarea });
        document.dispatchEvent(event);
      });

      expect(handler).not.toHaveBeenCalled();

      document.body.removeChild(textarea);
    });
  });

  describe('preventDefault', () => {
    it('should prevent default when preventDefault is true', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler, preventDefault: true },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      const event = new KeyboardEvent('keydown', { key: 'k' });
      const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

      act(() => {
        document.dispatchEvent(event);
      });

      expect(preventDefaultSpy).toHaveBeenCalled();
    });

    it('should not prevent default when preventDefault is false', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler, preventDefault: false },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      const event = new KeyboardEvent('keydown', { key: 'k' });
      const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

      act(() => {
        document.dispatchEvent(event);
      });

      expect(preventDefaultSpy).not.toHaveBeenCalled();
    });
  });

  describe('Enabled Option', () => {
    it('should not trigger shortcuts when disabled', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts, { enabled: false }));

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k' });
        document.dispatchEvent(event);
      });

      expect(handler).not.toHaveBeenCalled();
    });

    it('should trigger shortcuts when enabled', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts, { enabled: true }));

      act(() => {
        const event = new KeyboardEvent('keydown', { key: 'k' });
        document.dispatchEvent(event);
      });

      expect(handler).toHaveBeenCalledTimes(1);
    });
  });

  describe('Multiple Shortcuts', () => {
    it('should handle multiple shortcuts', () => {
      const handler1 = vi.fn();
      const handler2 = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler: handler1 },
        { key: 'j', handler: handler2 },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      act(() => {
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k' }));
      });
      expect(handler1).toHaveBeenCalledTimes(1);
      expect(handler2).not.toHaveBeenCalled();

      act(() => {
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'j' }));
      });
      expect(handler1).toHaveBeenCalledTimes(1);
      expect(handler2).toHaveBeenCalledTimes(1);
    });

    it('should only trigger first matching shortcut', () => {
      const handler1 = vi.fn();
      const handler2 = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler: handler1 },
        { key: 'k', handler: handler2 },
      ];

      renderHook(() => useKeyboardShortcuts(shortcuts));

      act(() => {
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k' }));
      });

      expect(handler1).toHaveBeenCalledTimes(1);
      expect(handler2).not.toHaveBeenCalled();
    });
  });

  describe('Cleanup', () => {
    it('should remove event listener on unmount', () => {
      const handler = vi.fn();
      const shortcuts: KeyboardShortcut[] = [
        { key: 'k', handler },
      ];

      const { unmount } = renderHook(() => useKeyboardShortcuts(shortcuts));

      unmount();

      act(() => {
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k' }));
      });

      expect(handler).not.toHaveBeenCalled();
    });
  });
});

describe('useEscapeKey', () => {
  it('should call handler when Escape is pressed', () => {
    const handler = vi.fn();

    renderHook(() => useEscapeKey(handler));

    act(() => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    });

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('should not call handler when disabled', () => {
    const handler = vi.fn();

    renderHook(() => useEscapeKey(handler, false));

    act(() => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    });

    expect(handler).not.toHaveBeenCalled();
  });

  it('should call handler even when focused on input', () => {
    const handler = vi.fn();

    renderHook(() => useEscapeKey(handler));

    const input = document.createElement('input');
    document.body.appendChild(input);
    input.focus();

    act(() => {
      const event = new KeyboardEvent('keydown', { key: 'Escape' });
      Object.defineProperty(event, 'target', { value: input });
      document.dispatchEvent(event);
    });

    expect(handler).toHaveBeenCalledTimes(1);

    document.body.removeChild(input);
  });
});

describe('formatShortcut', () => {
  it('should format single key', () => {
    expect(formatShortcut('k')).toBe('K');
  });

  it('should format key with Ctrl modifier', () => {
    expect(formatShortcut('k', { ctrl: true })).toBe('Ctrl+K');
  });

  it('should format key with Alt modifier', () => {
    expect(formatShortcut('k', { alt: true })).toBe('Alt+K');
  });

  it('should format key with Shift modifier', () => {
    expect(formatShortcut('k', { shift: true })).toBe('Shift+K');
  });

  it('should format key with Meta modifier', () => {
    expect(formatShortcut('k', { meta: true })).toBe('⌘+K');
  });

  it('should format key with multiple modifiers', () => {
    expect(formatShortcut('k', { ctrl: true, shift: true })).toBe('Ctrl+Shift+K');
  });

  it('should format special keys', () => {
    expect(formatShortcut('Escape')).toBe('Escape');
    expect(formatShortcut('Enter')).toBe('Enter');
  });
});
