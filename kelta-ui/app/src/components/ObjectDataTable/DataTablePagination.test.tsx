import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DataTablePagination } from './DataTablePagination'

describe('DataTablePagination', () => {
  const defaultProps = {
    page: 1,
    pageSize: 25,
    total: 100,
    onPageChange: vi.fn(),
    onPageSizeChange: vi.fn(),
  }

  it('renders page info', () => {
    render(<DataTablePagination {...defaultProps} />)
    expect(screen.getByText('Page 1 of 4')).toBeDefined()
  })

  it('renders record range', () => {
    render(<DataTablePagination {...defaultProps} />)
    expect(screen.getByText('1-25 of 100')).toBeDefined()
  })

  it('renders selection count when selected', () => {
    render(<DataTablePagination {...defaultProps} selectedCount={5} />)
    expect(screen.getByText('5 of 100 row(s) selected')).toBeDefined()
  })

  it('disables previous/first buttons on first page', () => {
    render(<DataTablePagination {...defaultProps} page={1} />)
    const firstPageBtn = screen.getByLabelText('First page')
    const prevBtn = screen.getByLabelText('Previous page')
    expect(firstPageBtn.hasAttribute('disabled')).toBe(true)
    expect(prevBtn.hasAttribute('disabled')).toBe(true)
  })

  it('disables next/last buttons on last page', () => {
    render(<DataTablePagination {...defaultProps} page={4} />)
    const lastPageBtn = screen.getByLabelText('Last page')
    const nextBtn = screen.getByLabelText('Next page')
    expect(lastPageBtn.hasAttribute('disabled')).toBe(true)
    expect(nextBtn.hasAttribute('disabled')).toBe(true)
  })

  it('enables all navigation buttons on middle page', () => {
    render(<DataTablePagination {...defaultProps} page={2} />)
    const firstPageBtn = screen.getByLabelText('First page')
    const prevBtn = screen.getByLabelText('Previous page')
    const nextBtn = screen.getByLabelText('Next page')
    const lastPageBtn = screen.getByLabelText('Last page')
    expect(firstPageBtn.hasAttribute('disabled')).toBe(false)
    expect(prevBtn.hasAttribute('disabled')).toBe(false)
    expect(nextBtn.hasAttribute('disabled')).toBe(false)
    expect(lastPageBtn.hasAttribute('disabled')).toBe(false)
  })

  it('calls onPageChange with correct page for next', () => {
    const onPageChange = vi.fn()
    render(<DataTablePagination {...defaultProps} page={2} onPageChange={onPageChange} />)
    fireEvent.click(screen.getByLabelText('Next page'))
    expect(onPageChange).toHaveBeenCalledWith(3)
  })

  it('calls onPageChange with correct page for previous', () => {
    const onPageChange = vi.fn()
    render(<DataTablePagination {...defaultProps} page={3} onPageChange={onPageChange} />)
    fireEvent.click(screen.getByLabelText('Previous page'))
    expect(onPageChange).toHaveBeenCalledWith(2)
  })

  it('calls onPageChange with 1 for first page', () => {
    const onPageChange = vi.fn()
    render(<DataTablePagination {...defaultProps} page={3} onPageChange={onPageChange} />)
    fireEvent.click(screen.getByLabelText('First page'))
    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('calls onPageChange with total pages for last page', () => {
    const onPageChange = vi.fn()
    render(<DataTablePagination {...defaultProps} page={2} onPageChange={onPageChange} />)
    fireEvent.click(screen.getByLabelText('Last page'))
    expect(onPageChange).toHaveBeenCalledWith(4)
  })

  it('shows 0 records when total is 0', () => {
    render(<DataTablePagination {...defaultProps} total={0} />)
    expect(screen.getByText('0-0 of 0')).toBeDefined()
  })

  it('shows correct range on last page with partial results', () => {
    render(<DataTablePagination {...defaultProps} page={4} total={90} />)
    expect(screen.getByText('76-90 of 90')).toBeDefined()
  })
})
