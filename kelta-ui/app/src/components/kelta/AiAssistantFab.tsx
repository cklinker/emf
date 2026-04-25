// AiAssistantFab.tsx — floating AI assistant button. The ONLY gradient surface.
import * as React from 'react'
import { Sparkles } from 'lucide-react'
import { cn } from '@/lib/utils'

export type AiAssistantFabProps = React.ButtonHTMLAttributes<HTMLButtonElement>

/**
 * Floating AI assistant launcher. Lives bottom-right, always visible.
 * The cyan→blue gradient appears here and on the logo mark — nowhere else.
 */
export const AiAssistantFab = React.forwardRef<HTMLButtonElement, AiAssistantFabProps>(
  ({ className, ...props }, ref) => (
    <button
      ref={ref}
      type="button"
      aria-label="Open AI assistant"
      className={cn(
        'fixed bottom-6 right-6 size-[52px] rounded-full',
        'flex items-center justify-center text-white',
        'shadow-[0_10px_24px_-8px_rgba(59,130,246,0.6)]',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
        'z-50',
        className
      )}
      style={{ background: 'linear-gradient(135deg, #06B6D4 0%, #3B82F6 100%)' }}
      {...props}
    >
      <Sparkles className="size-[22px]" aria-hidden="true" />
    </button>
  )
)

AiAssistantFab.displayName = 'AiAssistantFab'
