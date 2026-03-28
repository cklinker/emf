import { Button } from '@/components/ui/button'
import { Sparkles } from 'lucide-react'
import { useAiChat } from './AiChatContext'

export function AiChatTrigger() {
  const { togglePanel } = useAiChat()

  return (
    <Button
      onClick={togglePanel}
      size="icon"
      className="fixed bottom-6 right-6 z-50 h-12 w-12 rounded-full shadow-lg"
      title="AI Assistant (Ctrl+Shift+A)"
    >
      <Sparkles className="h-5 w-5" />
    </Button>
  )
}
