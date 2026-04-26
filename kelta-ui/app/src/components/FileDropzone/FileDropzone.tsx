import React, { useCallback, useRef, useState } from 'react'
import { Upload } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface FileDropzoneProps {
  /** Comma-separated list of accepted MIME types or extensions, e.g. ".json,.yaml,.yml". */
  accept?: string
  /** Maximum allowed file size in bytes. Files larger are rejected with onError. */
  maxBytes?: number
  /** Called with the dropped or selected file. */
  onFile: (file: File) => void
  /** Called when a file is rejected (too large, wrong type, read failure). */
  onError?: (message: string) => void
  /** Override the default helper text. */
  hint?: React.ReactNode
  className?: string
}

/**
 * Minimal drag-and-drop / click-to-browse file input. Native — no extra deps.
 * Used by the OpenAPI spec import dialog (and reusable for any future
 * upload-style flow).
 */
export function FileDropzone({
  accept,
  maxBytes,
  onFile,
  onError,
  hint,
  className,
}: FileDropzoneProps): React.ReactElement {
  const [isOver, setIsOver] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const handleFile = useCallback(
    (file: File | null | undefined) => {
      if (!file) return
      if (maxBytes && file.size > maxBytes) {
        onError?.(`File is too large (${(file.size / 1024 / 1024).toFixed(1)} MB)`)
        return
      }
      onFile(file)
    },
    [maxBytes, onError, onFile]
  )

  const onDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsOver(false)
    if (e.dataTransfer.files.length === 0) return
    handleFile(e.dataTransfer.files.item(0))
  }

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => inputRef.current?.click()}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          inputRef.current?.click()
        }
      }}
      onDragOver={(e) => {
        e.preventDefault()
        setIsOver(true)
      }}
      onDragLeave={() => setIsOver(false)}
      onDrop={onDrop}
      className={cn(
        'flex flex-col items-center justify-center gap-2 rounded-md border-2 border-dashed p-6 text-center transition-colors',
        isOver ? 'border-primary bg-primary/5' : 'border-border bg-muted/30',
        'hover:bg-muted/50 cursor-pointer focus:outline-none focus:ring-2 focus:ring-ring',
        className
      )}
    >
      <Upload className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
      <div className="space-y-0.5">
        <p className="text-sm font-medium">Drop a file or click to browse</p>
        <p className="text-xs text-muted-foreground">
          {hint ?? (accept ? `Accepted: ${accept}` : 'Any file type')}
        </p>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="hidden"
        onChange={(e) => handleFile(e.target.files?.item(0))}
      />
    </div>
  )
}
