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
        'fileViewer.preview': 'Preview {{name}}',
        'fileViewer.noPreview': 'Preview is not available for this file type.',
        'fileViewer.videoNotSupported': 'Your browser does not support video playback.',
        'fileViewer.audioNotSupported': 'Your browser does not support audio playback.',
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

const imageAttachment = {
  id: 'att-2',
  fileName: 'photo.jpg',
  fileSize: 204800,
  contentType: 'image/jpeg',
  uploadedBy: 'user@example.com',
  uploadedAt: new Date().toISOString(),
  downloadUrl: 'https://s3.example.com/photo.jpg',
}

describe('AttachmentsSection', () => {
  let mockApiClient: ReturnType<typeof createMockApiClient>

  beforeEach(() => {
    vi.clearAllMocks()
    mockApiClient = createMockApiClient()
  })

  it('renders attachment section with title', () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[]}
        />
      </TestWrapper>
    )

    expect(screen.getByTestId('attachments-section')).toBeDefined()
    expect(screen.getByText('Attachments')).toBeDefined()
  })

  it('shows empty state when no attachments', () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[]}
        />
      </TestWrapper>
    )

    expect(screen.getByTestId('attachments-empty')).toBeDefined()
    expect(screen.getByText('No attachments yet.')).toBeDefined()
  })

  it('renders attachments list when data exists', () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[sampleAttachment]}
        />
      </TestWrapper>
    )

    expect(screen.getByText('report.pdf')).toBeDefined()
    expect(screen.getByTestId('attachment-att-1')).toBeDefined()
  })

  it('renders upload button', () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[]}
        />
      </TestWrapper>
    )

    expect(screen.getByTestId('attachments-upload-button')).toBeDefined()
  })

  it('has a hidden file input for uploads', () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[]}
        />
      </TestWrapper>
    )

    const fileInput = screen.getByTestId('attachments-file-input')
    expect(fileInput).toBeDefined()
    expect(fileInput.getAttribute('type')).toBe('file')
  })

  it('triggers file upload when file is selected', async () => {
    mockApiClient.postFormData.mockResolvedValue(sampleAttachment)

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[]}
        />
      </TestWrapper>
    )

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

  it('renders download button for each attachment', () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[sampleAttachment]}
        />
      </TestWrapper>
    )

    const downloadButton = screen.getByTestId('attachment-download-att-1')
    expect(downloadButton).toBeDefined()
  })

  it('opens download URL when download button is clicked with downloadUrl', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[sampleAttachment]}
        />
      </TestWrapper>
    )

    fireEvent.click(screen.getByTestId('attachment-download-att-1'))

    await waitFor(() => {
      expect(openSpy).toHaveBeenCalledWith('https://s3.example.com/report.pdf', '_blank')
    })

    openSpy.mockRestore()
  })

  it('disables download button when downloadUrl is not present', () => {
    const attachmentWithoutUrl = { ...sampleAttachment, downloadUrl: null }

    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[attachmentWithoutUrl]}
        />
      </TestWrapper>
    )

    const downloadButton = screen.getByTestId('attachment-download-att-1')
    expect(downloadButton.hasAttribute('disabled')).toBe(true)
  })

  it('shows empty state when attachments prop is undefined', () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
        />
      </TestWrapper>
    )

    expect(screen.getByTestId('attachments-empty')).toBeDefined()
  })

  it('renders image thumbnail for image attachments', () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[imageAttachment]}
        />
      </TestWrapper>
    )

    const thumbnail = screen.getByTestId('attachment-thumbnail-att-2')
    expect(thumbnail).toBeDefined()
    expect(thumbnail.getAttribute('src')).toBe('https://s3.example.com/photo.jpg')
  })

  it('renders file-type icon for non-image attachments', () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[sampleAttachment]}
        />
      </TestWrapper>
    )

    // PDF attachment should show an icon, not a thumbnail
    expect(screen.getByTestId('attachment-icon-att-1')).toBeDefined()
  })

  it('falls back to icon when image thumbnail fails to load', async () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[imageAttachment]}
        />
      </TestWrapper>
    )

    expect(screen.getByTestId('attachment-thumbnail-att-2')).toBeDefined()

    // Simulate image load error
    const img = screen.getByTestId('attachment-thumbnail-att-2')
    fireEvent.error(img)

    await waitFor(() => {
      // After error, should show the icon fallback
      expect(screen.getByTestId('attachment-icon-att-2')).toBeDefined()
    })
  })

  it('opens file viewer when attachment is clicked', async () => {
    render(
      <TestWrapper>
        <AttachmentsSection
          collectionId="col-1"
          recordId="rec-1"
          apiClient={mockApiClient as never}
          attachments={[sampleAttachment]}
        />
      </TestWrapper>
    )

    expect(screen.getByTestId('attachment-preview-att-1')).toBeDefined()

    fireEvent.click(screen.getByTestId('attachment-preview-att-1'))

    await waitFor(() => {
      expect(screen.getByTestId('file-viewer-dialog')).toBeDefined()
    })
  })
})
