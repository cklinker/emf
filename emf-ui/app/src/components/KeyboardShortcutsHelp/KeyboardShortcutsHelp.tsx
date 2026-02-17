/**
 * KeyboardShortcutsHelp Component
 *
 * Modal overlay that displays all available keyboard shortcuts grouped
 * by category. Opened by pressing the `?` key.
 *
 * Features:
 * - Grouped shortcuts table (Navigation, Search, Record Actions, General)
 * - Styled kbd elements for key display
 * - Accessible modal with role="dialog" and role="presentation" backdrop
 * - Escape to close, close button in header
 */

import { useEffect, useRef } from 'react'
import { useI18n } from '../../context/I18nContext'
import { cn } from '@/lib/utils'

export interface KeyboardShortcutsHelpProps {
  isOpen: boolean
  onClose: () => void
}

interface ShortcutEntry {
  keys: string[][]
  descriptionKey: string
}

interface ShortcutGroup {
  titleKey: string
  shortcuts: ShortcutEntry[]
}

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/.test(navigator.userAgent)

const shortcutGroups: ShortcutGroup[] = [
  {
    titleKey: 'shortcuts.navigation',
    shortcuts: [
      { keys: [['g', 'h']], descriptionKey: 'shortcuts.goHome' },
      { keys: [['g', 'c']], descriptionKey: 'shortcuts.goCollections' },
      { keys: [['g', 'r']], descriptionKey: 'shortcuts.goResources' },
    ],
  },
  {
    titleKey: 'shortcuts.search',
    shortcuts: [
      {
        keys: isMac ? [['⌘', 'K']] : [['Ctrl', 'K']],
        descriptionKey: 'shortcuts.openSearch',
      },
      { keys: [['\u2044']], descriptionKey: 'shortcuts.focusFilter' },
    ],
  },
  {
    titleKey: 'shortcuts.recordActions',
    shortcuts: [
      { keys: [['e']], descriptionKey: 'shortcuts.editRecord' },
      { keys: [['n']], descriptionKey: 'shortcuts.newRecord' },
      { keys: [['Backspace']], descriptionKey: 'shortcuts.goBack' },
    ],
  },
  {
    titleKey: 'shortcuts.general',
    shortcuts: [
      { keys: [['?']], descriptionKey: 'shortcuts.showHelp' },
      { keys: [['Esc']], descriptionKey: 'shortcuts.closeDialog' },
    ],
  },
]

export function KeyboardShortcutsHelp({ isOpen, onClose }: KeyboardShortcutsHelpProps) {
  const { t } = useI18n()
  const modalRef = useRef<HTMLDivElement>(null)

  // Focus trap and escape handling
  useEffect(() => {
    if (!isOpen) return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        onClose()
      }
    }

    document.addEventListener('keydown', handleKeyDown, true)

    // Focus the modal when opened
    if (modalRef.current) {
      modalRef.current.focus()
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown, true)
    }
  }, [isOpen, onClose])

  if (!isOpen) return null

  // Fix: the `/` key should display as `/`
  const displayKey = (key: string) => (key === '\u2044' ? '/' : key)

  return (
    <div
      className={cn('fixed inset-0 z-[9999] bg-black/50 flex items-center justify-center p-4')}
      role="presentation"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
      data-testid="keyboard-shortcuts-help"
    >
      <div
        className={cn(
          'w-full max-w-[560px] max-h-[80vh] bg-card rounded-xl shadow-2xl border border-border overflow-y-auto'
        )}
        role="dialog"
        aria-modal="true"
        aria-label={t('shortcuts.title')}
        ref={modalRef}
        tabIndex={-1}
      >
        <div
          className={cn(
            'flex items-center justify-between px-6 py-4 border-b border-border sticky top-0 bg-card z-10'
          )}
        >
          <h2 className={cn('text-lg font-semibold text-foreground m-0')}>
            {t('shortcuts.title')}
          </h2>
          <button
            type="button"
            className={cn(
              'flex items-center justify-center w-8 h-8 rounded-md hover:bg-accent cursor-pointer text-xl text-muted-foreground transition-colors border-0 bg-transparent leading-none'
            )}
            onClick={onClose}
            aria-label={t('common.close')}
          >
            &times;
          </button>
        </div>

        {shortcutGroups.map((group) => (
          <div
            key={group.titleKey}
            className={cn('px-6 py-4 border-b border-border last:border-b-0')}
          >
            <h3 className={cn('text-sm font-semibold text-foreground mb-3 m-0')}>
              {t(group.titleKey)}
            </h3>
            {group.shortcuts.map((shortcut) => (
              <div
                key={shortcut.descriptionKey}
                className={cn('flex items-center justify-between py-1.5')}
              >
                <div className={cn('flex items-center gap-1')}>
                  {shortcut.keys.map((keyCombo, comboIdx) => (
                    <span key={comboIdx} className={cn('flex items-center gap-1')}>
                      {keyCombo.map((key, keyIdx) => (
                        <kbd
                          key={keyIdx}
                          className={cn(
                            'inline-flex items-center justify-center min-w-[24px] h-6 px-1.5 rounded border border-border bg-muted text-xs font-mono text-muted-foreground shadow-sm'
                          )}
                        >
                          {displayKey(key)}
                        </kbd>
                      ))}
                    </span>
                  ))}
                </div>
                <span className={cn('text-sm text-muted-foreground')}>
                  {t(shortcut.descriptionKey)}
                </span>
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}

export default KeyboardShortcutsHelp
