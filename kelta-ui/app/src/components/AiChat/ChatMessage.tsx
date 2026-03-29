import { cn } from '@/lib/utils'
import { User, Bot, Loader2 } from 'lucide-react'
import type { ChatMessage as ChatMessageType } from './types'

interface ChatMessageProps {
  message: ChatMessageType
}

export function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === 'user'

  // Don't render empty assistant messages (proposals are rendered separately)
  if (!isUser && !message.content) return null

  return (
    <div className={cn('flex gap-3 px-4 py-3', isUser ? 'bg-muted/30' : '')}>
      <div
        className={cn(
          'flex h-7 w-7 shrink-0 items-center justify-center rounded-full',
          isUser ? 'bg-primary text-primary-foreground' : 'bg-muted'
        )}
      >
        {isUser ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
      </div>
      <div className="min-w-0 flex-1 space-y-2">
        <p className="text-sm font-medium">{isUser ? 'You' : 'AI Assistant'}</p>
        <div className="prose prose-sm max-w-none dark:prose-invert">
          <p className="whitespace-pre-wrap text-sm leading-relaxed">{message.content}</p>
        </div>
      </div>
    </div>
  )
}

interface StreamingMessageProps {
  text: string
  isGenerating: boolean
}

export function StreamingMessage({ text, isGenerating }: StreamingMessageProps) {
  return (
    <div className="flex gap-3 px-4 py-3">
      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-muted">
        {isGenerating && !text ? (
          <Loader2 className="h-4 w-4 animate-spin text-primary" />
        ) : (
          <Bot className="h-4 w-4" />
        )}
      </div>
      <div className="min-w-0 flex-1 space-y-2">
        <p className="text-sm font-medium">AI Assistant</p>
        {text ? (
          <div className="prose prose-sm max-w-none dark:prose-invert">
            <p className="whitespace-pre-wrap text-sm leading-relaxed">
              {text}
              <span className="ml-1 inline-block h-4 w-1 animate-pulse bg-foreground" />
            </p>
          </div>
        ) : (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-3 w-3 animate-spin" />
            <span>{isGenerating ? 'Thinking...' : 'Generating...'}</span>
          </div>
        )}
      </div>
    </div>
  )
}
