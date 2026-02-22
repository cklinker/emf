import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AttachmentsSection } from './AttachmentsSection'

// Mock I18n context
vi.mock('../../context/I18nContext', () => ({
  useI18n: vi.fn(() => ({
    locale: 'en',
    setLocale: vi.fn(),
    t: (key: string) => {
      const translations: Record<string, string> = {
        'attachments.title': 'Attachments',
        'attachments.upload': 'Upload',
        'attachments.uploading': 'Uploading...',
        'attachments.download': 'Download',
        'attachments.delete': 'Delete',
        'attachments.confirmDelete': 'Delete this attachment?',
        'attachments.empty': 'No attachments yet.',
        'attachments.comingSoon': 'Attachments feature coming soon.',
      }
      return translations[key] || key
    },
  })),
}))

function createMockApiClient() {
  return {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
    postFormData: vi.fn(),
  }
}

function TestWrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

const sampleAttachment = {
  id: 'att-1',
  fileName: 'report.pdf',
  fileSize: 1048576,
  contentType: 'application/pdf',
  uploadedBy: 'user@example.com',
  uploadedAt: new Date().toISOString(),
  downloadUrl: 'https://s3.example.com/report.pdf',
}

describe('AttachmentsSection', () => {
  let mockApiClient: ReturnType<typeof createMockApiClient>

  beforeEach(() => {
    vi.clearAllMocks()
    mockApiClient = createMockApiClient()
  })

  it('renders attachment section with title', async () => {
    mockApiClient.get.mockResolvedValue([])

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByTestId('attachments-section')).toBeDefined()
      expect(screen.getByText('Attachments')).toBeDefined()
    })
  })

  it('shows empty state when no attachments', async () => {
    mockApiClient.get.mockResolvedValue([])

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByTestId('attachments-empty')).toBeDefined()
      expect(screen.getByText('No attachments yet.')).toBeDefined()
    })
  })

  it('renders attachments list when data exists', async () => {
    mockApiClient.get.mockResolvedValue([sampleAttachment])

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByText('report.pdf')).toBeDefined()
      expect(screen.getByTestId('attachment-att-1')).toBeDefined()
    })
  })

  it('renders upload button', async () => {
    mockApiClient.get.mockResolvedValue([])

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByTestId('attachments-upload-button')).toBeDefined()
    })
  })

  it('has a hidden file input for uploads', async () => {
    mockApiClient.get.mockResolvedValue([])

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      const fileInput = screen.getByTestId('attachments-file-input')
      expect(fileInput).toBeDefined()
      expect(fileInput.getAttribute('type')).toBe('file')
    })
  })

  it('triggers file upload when file is selected', async () => {
    mockApiClient.get.mockResolvedValue([])
    mockApiClient.postFormData.mockResolvedValue(sampleAttachment)

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByTestId('attachments-file-input')).toBeDefined()
    })

    const fileInput = screen.getByTestId('attachments-file-input')
    const file = new File(['test content'], 'test.txt', { type: 'text/plain' })

    fireEvent.change(fileInput, { target: { files: [file] } })

    await waitFor(() => {
      expect(mockApiClient.postFormData).toHaveBeenCalledTimes(1)
      const [url, formData] = mockApiClient.postFormData.mock.calls[0]
      expect(url).toBe('/control/attachments/col-1/rec-1')
      expect(formData).toBeInstanceOf(FormData)
    })
  })

  it('renders download button for each attachment', async () => {
    mockApiClient.get.mockResolvedValue([sampleAttachment])

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      const downloadButton = screen.getByTestId('attachment-download-att-1')
      expect(downloadButton).toBeDefined()
    })
  })

  it('opens download URL when download button is clicked with downloadUrl', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    mockApiClient.get.mockResolvedValue([sampleAttachment])

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByTestId('attachment-download-att-1')).toBeDefined()
    })

    fireEvent.click(screen.getByTestId('attachment-download-att-1'))

    await waitFor(() => {
      expect(openSpy).toHaveBeenCalledWith('https://s3.example.com/report.pdf', '_blank')
    })

    openSpy.mockRestore()
  })

  it('disables download button when downloadUrl is not present', async () => {
    const attachmentWithoutUrl = { ...sampleAttachment, downloadUrl: null }
    mockApiClient.get.mockResolvedValue([attachmentWithoutUrl])

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      const downloadButton = screen.getByTestId('attachment-download-att-1')
      expect(downloadButton.hasAttribute('disabled')).toBe(true)
    })
  })

  it('shows coming soon when API returns 404', async () => {
    mockApiClient.get.mockRejectedValue(new Error('Not Found'))

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    await waitFor(() => {
      expect(screen.getByTestId('attachments-coming-soon')).toBeDefined()
      expect(screen.getByText('Attachments feature coming soon.')).toBeDefined()
    })
  })
})
