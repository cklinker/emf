/**
 * LookupSelect Component
 *
 * Searchable dropdown for selecting records from a related collection.
 * Used in resource forms for LOOKUP, MASTER_DETAIL, and REFERENCE field types.
 */

import React, { useState, useCallback, useRef, useEffect, useMemo } from 'react'
import styles from './LookupSelect.module.css'

export interface LookupOption {
  id: string
  label: string
}

export interface LookupSelectProps {
  id?: string
  name?: string
  value: string
  options: LookupOption[]
  onChange: (value: string) => void
  placeholder?: string
  required?: boolean
  disabled?: boolean
  error?: boolean
  className?: string
  'data-testid'?: string
}

export function LookupSelect({
  id,
  name,
  value,
  options,
  onChange,
  placeholder = 'Select...',
  required = false,
  disabled = false,
  error = false,
  className,
  'data-testid': testId,
}: LookupSelectProps): React.ReactElement {
  const [isOpen, setIsOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [highlightedIndex, setHighlightedIndex] = useState(-1)
  const containerRef = useRef<HTMLDivElement>(null)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const listRef = useRef<HTMLUListElement>(null)

  const selectedOption = useMemo(() => options.find((o) => o.id === value), [options, value])

  const filteredOptions = useMemo(() => {
    if (!search.trim()) return options
    const lower = search.toLowerCase()
    return options.filter((o) => o.label.toLowerCase().includes(lower))
  }, [options, search])

  const open = useCallback(() => {
    if (disabled) return
    setIsOpen(true)
    setSearch('')
    setHighlightedIndex(-1)
    requestAnimationFrame(() => {
      searchInputRef.current?.focus()
    })
  }, [disabled])

  const close = useCallback(() => {
    setIsOpen(false)
    setSearch('')
    setHighlightedIndex(-1)
  }, [])

  const handleSelect = useCallback(
    (optionId: string) => {
      onChange(optionId)
      close()
    },
    [onChange, close]
  )

  const handleClear = useCallback(
    (e: React.MouseEvent | React.KeyboardEvent) => {
      e.stopPropagation()
      onChange('')
      close()
    },
    [onChange, close]
  )

  const handleTriggerClick = useCallback(() => {
    if (isOpen) {
      close()
    } else {
      open()
    }
  }, [isOpen, open, close])

  const scrollOptionIntoView = useCallback((index: number) => {
    requestAnimationFrame(() => {
      const list = listRef.current
      if (!list) return
      const item = list.children[index] as HTMLElement
      if (item) {
        item.scrollIntoView({ block: 'nearest' })
      }
    })
  }, [])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!isOpen) {
        if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
          e.preventDefault()
          open()
        }
        return
      }

      switch (e.key) {
        case 'Escape':
          e.preventDefault()
          close()
          containerRef.current?.querySelector<HTMLButtonElement>('[data-trigger]')?.focus()
          break
        case 'ArrowDown':
          e.preventDefault()
          setHighlightedIndex((prev) => {
            const next = prev < filteredOptions.length - 1 ? prev + 1 : 0
            scrollOptionIntoView(next)
            return next
          })
          break
        case 'ArrowUp':
          e.preventDefault()
          setHighlightedIndex((prev) => {
            const next = prev > 0 ? prev - 1 : filteredOptions.length - 1
            scrollOptionIntoView(next)
            return next
          })
          break
        case 'Enter':
          e.preventDefault()
          if (highlightedIndex >= 0 && highlightedIndex < filteredOptions.length) {
            handleSelect(filteredOptions[highlightedIndex].id)
          }
          break
      }
    },
    [isOpen, open, close, filteredOptions, highlightedIndex, handleSelect, scrollOptionIntoView]
  )

  // Close on click outside
  useEffect(() => {
    if (!isOpen) return

    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        close()
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen, close])

  const displayText = selectedOption ? selectedOption.label : value ? value : ''

  const showClear = value && !required && !disabled

  const listboxId = `${id || name || 'lookup'}-listbox`

  return (
    // eslint-disable-next-line jsx-a11y/no-static-element-interactions
    <div
      ref={containerRef}
      className={`${styles.container} ${className || ''}`}
      onKeyDown={handleKeyDown}
      data-testid={testId}
    >
      <button
        type="button"
        id={id}
        data-trigger="true"
        className={`${styles.trigger} ${error ? styles.triggerError : ''} ${disabled ? styles.triggerDisabled : ''}`}
        onClick={handleTriggerClick}
        disabled={disabled}
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={isOpen ? listboxId : undefined}
        data-testid={testId ? `${testId}-trigger` : undefined}
      >
        <span
          className={`${styles.triggerText} ${!displayText ? styles.placeholder : ''} ${value && !selectedOption ? styles.unknownValue : ''}`}
        >
          {displayText || placeholder}
        </span>
        <span className={styles.triggerIcons}>
          {showClear && (
            <span
              role="button"
              tabIndex={-1}
              className={styles.clearButton}
              onClick={handleClear}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  handleClear(e)
                }
              }}
              aria-label="Clear selection"
              data-testid={testId ? `${testId}-clear` : undefined}
            >
              &times;
            </span>
          )}
          <span
            className={`${styles.chevron} ${isOpen ? styles.chevronOpen : ''}`}
            aria-hidden="true"
          >
            &#9662;
          </span>
        </span>
      </button>

      {isOpen && (
        <div
          className={styles.dropdown}
          role="presentation"
          data-testid={testId ? `${testId}-dropdown` : undefined}
        >
          <input
            ref={searchInputRef}
            type="text"
            className={styles.searchInput}
            value={search}
            onChange={(e) => {
              setSearch(e.target.value)
              setHighlightedIndex(-1)
            }}
            placeholder="Search..."
            aria-label="Search options"
            data-testid={testId ? `${testId}-search` : undefined}
          />
          <ul
            ref={listRef}
            id={listboxId}
            className={styles.optionsList}
            role="listbox"
            aria-label={name || 'Options'}
          >
            {filteredOptions.length === 0 ? (
              <li className={styles.noResults} role="option" aria-selected={false}>
                No records found
              </li>
            ) : (
              filteredOptions.map((option, index) => (
                <li
                  key={option.id}
                  role="option"
                  aria-selected={option.id === value}
                  className={`${styles.option} ${option.id === value ? styles.optionSelected : ''} ${index === highlightedIndex ? styles.optionHighlighted : ''}`}
                  onClick={() => handleSelect(option.id)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      handleSelect(option.id)
                    }
                  }}
                  onMouseEnter={() => setHighlightedIndex(index)}
                  data-testid={testId ? `${testId}-option-${index}` : undefined}
                >
                  {option.label}
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  )
}

export default LookupSelect
