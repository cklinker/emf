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
import styles from './KeyboardShortcutsHelp.module.css'

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
      className={styles.overlay}
      role="presentation"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
      data-testid="keyboard-shortcuts-help"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-label={t('shortcuts.title')}
        ref={modalRef}
        tabIndex={-1}
      >
        <div className={styles.header}>
          <h2 className={styles.title}>{t('shortcuts.title')}</h2>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
            aria-label={t('common.close')}
          >
            &times;
          </button>
        </div>

        {shortcutGroups.map((group) => (
          <div key={group.titleKey} className={styles.group}>
            <h3 className={styles.groupTitle}>{t(group.titleKey)}</h3>
            {group.shortcuts.map((shortcut) => (
              <div key={shortcut.descriptionKey} className={styles.shortcutRow}>
                <div className={styles.keys}>
                  {shortcut.keys.map((keyCombo, comboIdx) => (
                    <span key={comboIdx} className={styles.keys}>
                      {keyCombo.map((key, keyIdx) => (
                        <kbd key={keyIdx} className={styles.kbd}>
                          {displayKey(key)}
                        </kbd>
                      ))}
                    </span>
                  ))}
                </div>
                <span className={styles.description}>{t(shortcut.descriptionKey)}</span>
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}

export default KeyboardShortcutsHelp
