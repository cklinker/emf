import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { FileViewer } from './FileViewer'
import type { FileViewerAttachment } from './FileViewer'

// Mock I18n context
vi.mock('../../context/I18nContext', () => ({
  useI18n: vi.fn(() => ({
    locale: 'en',
    setLocale: vi.fn(),
    t: (key: string) => {
      const translations: Record<string, string> = {
        'attachments.download': 'Download',
        'fileViewer.preview': 'Preview {{name}}',
        'fileViewer.noPreview': 'Preview is not available for this file type.',
        'fileViewer.videoNotSupported': 'Your browser does not support video playback.',
        'fileViewer.audioNotSupported': 'Your browser does not support audio playback.',
      }
      return translations[key] || key
    },
  })),
}))

function makeAttachment(overrides: Partial<FileViewerAttachment> = {}): FileViewerAttachment {
  return {
    id: 'att-1',
    fileName: 'test-file.png',
    fileSize: 2048,
    contentType: 'image/png',
    downloadUrl: 'https://s3.example.com/test-file.png',
    ...overrides,
  }
}

describe('FileViewer', () => {
  it('does not render when attachment is null', () => {
    const { container } = render(
      <FileViewer attachment={null} onClose={vi.fn()} onDownload={vi.fn()} />
    )
    expect(container.innerHTML).toBe('')
  })

  it('renders dialog with file name as title', () => {
    render(
      <FileViewer
        attachment={makeAttachment({ fileName: 'photo.jpg' })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    expect(screen.getByText('photo.jpg')).toBeDefined()
  })

  it('renders image preview for image content type', () => {
    render(
      <FileViewer
        attachment={makeAttachment({
          contentType: 'image/png',
          downloadUrl: 'https://s3.example.com/photo.png',
        })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    expect(screen.getByTestId('file-viewer-image')).toBeDefined()
    const img = screen.getByRole('img')
    expect(img.getAttribute('src')).toBe('https://s3.example.com/photo.png')
  })

  it('renders PDF preview for application/pdf', () => {
    render(
      <FileViewer
        attachment={makeAttachment({
          fileName: 'doc.pdf',
          contentType: 'application/pdf',
          downloadUrl: 'https://s3.example.com/doc.pdf',
        })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    expect(screen.getByTestId('file-viewer-pdf')).toBeDefined()
    const iframe = screen.getByTitle('doc.pdf')
    expect(iframe.getAttribute('src')).toBe('https://s3.example.com/doc.pdf')
  })

  it('renders video preview for video content type', () => {
    render(
      <FileViewer
        attachment={makeAttachment({
          fileName: 'clip.mp4',
          contentType: 'video/mp4',
          downloadUrl: 'https://s3.example.com/clip.mp4',
        })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    expect(screen.getByTestId('file-viewer-video')).toBeDefined()
  })

  it('renders audio preview for audio content type', () => {
    render(
      <FileViewer
        attachment={makeAttachment({
          fileName: 'song.mp3',
          contentType: 'audio/mpeg',
          downloadUrl: 'https://s3.example.com/song.mp3',
        })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    expect(screen.getByTestId('file-viewer-audio')).toBeDefined()
  })

  it('renders text preview for text/plain', () => {
    render(
      <FileViewer
        attachment={makeAttachment({
          fileName: 'readme.txt',
          contentType: 'text/plain',
          downloadUrl: 'https://s3.example.com/readme.txt',
        })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    expect(screen.getByTestId('file-viewer-text')).toBeDefined()
  })

  it('renders text preview for code types', () => {
    render(
      <FileViewer
        attachment={makeAttachment({
          fileName: 'data.json',
          contentType: 'application/json',
          downloadUrl: 'https://s3.example.com/data.json',
        })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    expect(screen.getByTestId('file-viewer-text')).toBeDefined()
  })

  it('renders no-preview fallback for archive types', () => {
    render(
      <FileViewer
        attachment={makeAttachment({
          fileName: 'archive.zip',
          contentType: 'application/zip',
          downloadUrl: 'https://s3.example.com/archive.zip',
        })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    expect(screen.getByTestId('file-viewer-no-preview')).toBeDefined()
    expect(screen.getByText('Preview is not available for this file type.')).toBeDefined()
  })

  it('renders no-preview when downloadUrl is null', () => {
    render(
      <FileViewer
        attachment={makeAttachment({
          contentType: 'image/png',
          downloadUrl: null,
        })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    expect(screen.getByTestId('file-viewer-no-preview')).toBeDefined()
  })

  it('calls onDownload when download button is clicked', () => {
    const onDownload = vi.fn()
    const attachment = makeAttachment()
    render(<FileViewer attachment={attachment} onClose={vi.fn()} onDownload={onDownload} />)

    fireEvent.click(screen.getByTestId('file-viewer-download'))
    expect(onDownload).toHaveBeenCalledWith(attachment)
  })

  it('shows file size and content type in footer', () => {
    render(
      <FileViewer
        attachment={makeAttachment({ fileSize: 1048576, contentType: 'image/png' })}
        onClose={vi.fn()}
        onDownload={vi.fn()}
      />
    )
    // formatFileSize(1048576) = "1.0 MB"
    expect(screen.getByText(/1\.0 MB/)).toBeDefined()
    expect(screen.getByText(/image\/png/)).toBeDefined()
  })
})
