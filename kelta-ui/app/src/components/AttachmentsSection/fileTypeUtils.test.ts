import { describe, it, expect } from 'vitest'
import { getFileTypeInfo, isImageType, isPreviewable, formatFileSize } from './fileTypeUtils'

describe('fileTypeUtils', () => {
  describe('getFileTypeInfo', () => {
    it('classifies image types', () => {
      const info = getFileTypeInfo('image/png')
      expect(info.category).toBe('image')
      expect(info.color).toBe('text-emerald-500')
      expect(info.isPreviewable).toBe(true)
    })

    it('classifies image/jpeg', () => {
      expect(getFileTypeInfo('image/jpeg').category).toBe('image')
    })

    it('classifies image/gif', () => {
      expect(getFileTypeInfo('image/gif').category).toBe('image')
    })

    it('classifies image/webp', () => {
      expect(getFileTypeInfo('image/webp').category).toBe('image')
    })

    it('classifies image/svg+xml', () => {
      expect(getFileTypeInfo('image/svg+xml').category).toBe('image')
    })

    it('classifies PDF', () => {
      const info = getFileTypeInfo('application/pdf')
      expect(info.category).toBe('pdf')
      expect(info.color).toBe('text-red-500')
      expect(info.isPreviewable).toBe(true)
    })

    it('classifies video types', () => {
      const info = getFileTypeInfo('video/mp4')
      expect(info.category).toBe('video')
      expect(info.color).toBe('text-purple-500')
      expect(info.isPreviewable).toBe(true)
    })

    it('classifies video/webm', () => {
      expect(getFileTypeInfo('video/webm').category).toBe('video')
    })

    it('classifies audio types', () => {
      const info = getFileTypeInfo('audio/mpeg')
      expect(info.category).toBe('audio')
      expect(info.color).toBe('text-orange-500')
      expect(info.isPreviewable).toBe(true)
    })

    it('classifies audio/wav', () => {
      expect(getFileTypeInfo('audio/wav').category).toBe('audio')
    })

    it('classifies text/plain', () => {
      const info = getFileTypeInfo('text/plain')
      expect(info.category).toBe('text')
      expect(info.color).toBe('text-gray-500')
      expect(info.isPreviewable).toBe(true)
    })

    it('classifies text/csv', () => {
      expect(getFileTypeInfo('text/csv').category).toBe('text')
    })

    it('classifies text/markdown', () => {
      expect(getFileTypeInfo('text/markdown').category).toBe('text')
    })

    it('classifies code types', () => {
      const info = getFileTypeInfo('application/json')
      expect(info.category).toBe('code')
      expect(info.color).toBe('text-cyan-500')
      expect(info.isPreviewable).toBe(true)
    })

    it('classifies text/html as code', () => {
      expect(getFileTypeInfo('text/html').category).toBe('code')
    })

    it('classifies text/css as code', () => {
      expect(getFileTypeInfo('text/css').category).toBe('code')
    })

    it('classifies application/xml as code', () => {
      expect(getFileTypeInfo('application/xml').category).toBe('code')
    })

    it('classifies text/javascript as code', () => {
      expect(getFileTypeInfo('text/javascript').category).toBe('code')
    })

    it('classifies spreadsheet types', () => {
      const info = getFileTypeInfo('application/vnd.ms-excel')
      expect(info.category).toBe('spreadsheet')
      expect(info.color).toBe('text-green-600')
      expect(info.isPreviewable).toBe(false)
    })

    it('classifies xlsx as spreadsheet', () => {
      expect(
        getFileTypeInfo('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
          .category
      ).toBe('spreadsheet')
    })

    it('classifies archive types', () => {
      const info = getFileTypeInfo('application/zip')
      expect(info.category).toBe('archive')
      expect(info.color).toBe('text-yellow-600')
      expect(info.isPreviewable).toBe(false)
    })

    it('classifies gzip as archive', () => {
      expect(getFileTypeInfo('application/gzip').category).toBe('archive')
    })

    it('classifies tar as archive', () => {
      expect(getFileTypeInfo('application/x-tar').category).toBe('archive')
    })

    it('returns other for unknown types', () => {
      const info = getFileTypeInfo('application/octet-stream')
      expect(info.category).toBe('other')
      expect(info.color).toBe('text-muted-foreground')
      expect(info.isPreviewable).toBe(false)
    })

    it('handles null content type', () => {
      const info = getFileTypeInfo(null)
      expect(info.category).toBe('other')
      expect(info.isPreviewable).toBe(false)
    })

    it('handles undefined content type', () => {
      const info = getFileTypeInfo(undefined)
      expect(info.category).toBe('other')
    })

    it('handles empty string', () => {
      const info = getFileTypeInfo('')
      expect(info.category).toBe('other')
    })

    it('strips MIME parameters (charset)', () => {
      const info = getFileTypeInfo('text/plain; charset=utf-8')
      expect(info.category).toBe('text')
    })

    it('is case-insensitive', () => {
      expect(getFileTypeInfo('IMAGE/PNG').category).toBe('image')
      expect(getFileTypeInfo('Application/PDF').category).toBe('pdf')
      expect(getFileTypeInfo('VIDEO/MP4').category).toBe('video')
    })
  })

  describe('isImageType', () => {
    it('returns true for image types', () => {
      expect(isImageType('image/png')).toBe(true)
      expect(isImageType('image/jpeg')).toBe(true)
      expect(isImageType('image/gif')).toBe(true)
      expect(isImageType('image/webp')).toBe(true)
    })

    it('returns false for non-image types', () => {
      expect(isImageType('application/pdf')).toBe(false)
      expect(isImageType('video/mp4')).toBe(false)
      expect(isImageType('text/plain')).toBe(false)
    })

    it('returns false for null/undefined', () => {
      expect(isImageType(null)).toBe(false)
      expect(isImageType(undefined)).toBe(false)
    })

    it('handles MIME parameters', () => {
      expect(isImageType('image/png; charset=utf-8')).toBe(true)
    })
  })

  describe('isPreviewable', () => {
    it('returns true for previewable types', () => {
      expect(isPreviewable('image/png')).toBe(true)
      expect(isPreviewable('application/pdf')).toBe(true)
      expect(isPreviewable('video/mp4')).toBe(true)
      expect(isPreviewable('audio/mpeg')).toBe(true)
      expect(isPreviewable('text/plain')).toBe(true)
      expect(isPreviewable('application/json')).toBe(true)
    })

    it('returns false for non-previewable types', () => {
      expect(isPreviewable('application/zip')).toBe(false)
      expect(isPreviewable('application/vnd.ms-excel')).toBe(false)
      expect(isPreviewable('application/octet-stream')).toBe(false)
    })

    it('returns false for null/undefined', () => {
      expect(isPreviewable(null)).toBe(false)
      expect(isPreviewable(undefined)).toBe(false)
    })
  })

  describe('formatFileSize', () => {
    it('formats 0 bytes', () => {
      expect(formatFileSize(0)).toBe('0 B')
    })

    it('formats bytes', () => {
      expect(formatFileSize(512)).toBe('512 B')
    })

    it('formats kilobytes', () => {
      expect(formatFileSize(1024)).toBe('1.0 KB')
      expect(formatFileSize(1536)).toBe('1.5 KB')
    })

    it('formats megabytes', () => {
      expect(formatFileSize(1048576)).toBe('1.0 MB')
      expect(formatFileSize(5242880)).toBe('5.0 MB')
    })

    it('formats gigabytes', () => {
      expect(formatFileSize(1073741824)).toBe('1.0 GB')
    })
  })
})
