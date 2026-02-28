import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'

interface DataPathFieldsProps {
  nodeId: string
  inputPath: string
  outputPath: string
  resultPath: string
  onUpdate: (data: Record<string, unknown>) => void
}

export function DataPathFields({
  nodeId,
  inputPath,
  outputPath,
  resultPath,
  onUpdate,
}: DataPathFieldsProps) {
  return (
    <Accordion type="single" collapsible>
      <AccordionItem value="data-paths" className="border-none">
        <AccordionTrigger className="py-2 text-xs font-medium text-muted-foreground hover:no-underline">
          Data Paths
        </AccordionTrigger>
        <AccordionContent className="flex flex-col gap-3 pb-0">
          <div>
            <Label htmlFor={`input-path-${nodeId}`} className="text-xs">
              InputPath
            </Label>
            <Input
              id={`input-path-${nodeId}`}
              value={inputPath}
              onChange={(e) => onUpdate({ inputPath: e.target.value })}
              className="mt-1 h-8 font-mono text-xs"
              placeholder="$.input"
            />
          </div>
          <div>
            <Label htmlFor={`output-path-${nodeId}`} className="text-xs">
              OutputPath
            </Label>
            <Input
              id={`output-path-${nodeId}`}
              value={outputPath}
              onChange={(e) => onUpdate({ outputPath: e.target.value })}
              className="mt-1 h-8 font-mono text-xs"
              placeholder="$.output"
            />
          </div>
          <div>
            <Label htmlFor={`result-path-${nodeId}`} className="text-xs">
              ResultPath
            </Label>
            <Input
              id={`result-path-${nodeId}`}
              value={resultPath}
              onChange={(e) => onUpdate({ resultPath: e.target.value })}
              className="mt-1 h-8 font-mono text-xs"
              placeholder="$.result"
            />
          </div>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  )
}
