import client from './client'
import { AnalysisRequest, AnalysisResponse } from '../types'

export const analysisApi = {
  analyze: (datasetId: number, data: AnalysisRequest) =>
    client.post<AnalysisResponse>(`/analysis/${datasetId}`, data).then((r) => r.data),

  analyzeStream: (datasetId: number, data: AnalysisRequest, onEvent: (event: string, data: string) => void) => {
    return fetch(`/api/analysis/${datasetId}/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('auth-storage') ? JSON.parse(localStorage.getItem('auth-storage')!).state.token : ''}`,
      },
      body: JSON.stringify(data),
    }).then(async (response) => {
      const reader = response.body?.getReader()
      if (!reader) return

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const jsonStr = line.substring(5).trim()
            try {
              const parsed = JSON.parse(jsonStr)
              if (parsed.event === 'message') {
                const eventData = JSON.parse(parsed.data)
                onEvent(eventData.type, eventData.data)
              }
            } catch (e) {
              // ignore parse errors
            }
          }
        }
      }
    })
  },
}
