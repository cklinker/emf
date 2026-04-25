import { AiAssistantFab } from '@/components/kelta'
import { useAiChat } from './AiChatContext'

export function AiChatTrigger() {
  const { togglePanel } = useAiChat()

  return <AiAssistantFab onClick={togglePanel} title="AI assistant (Ctrl+Shift+A)" />
}
