import React, { useRef, useEffect, useState } from 'react'
import { Input, Button, Spin, Tooltip, Typography } from 'antd'
import { SendOutlined, CopyOutlined } from '@ant-design/icons'
import { ChatMessage, CleaningExecutionRequest } from '../types'
import SqlBlock from './SqlBlock'
import { CleaningCard } from './CleaningCard'

const { Text } = Typography

interface Props {
  messages: ChatMessage[]
  onSend: (question: string) => void
  loading: boolean
  onExecuteCleaning?: (request: CleaningExecutionRequest) => void
  onSaveAsCleaning?: (request: CleaningExecutionRequest) => void
}

const formatTimestamp = (value: number) =>
  new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))

const ChatPanel: React.FC<Props> = ({ messages, onSend, loading, onExecuteCleaning, onSaveAsCleaning }) => {
  const [input, setInput] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [messages])

  const handleSend = () => {
    const trimmed = input.trim()
    if (!trimmed || loading) return
    onSend(trimmed)
    setInput('')
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const exampleQuestions = ['总共有多少条数据？', '各类别数量分布如何？', '数量最多的是什么？']

  return (
    <div className="chat-container">
      <div className="chat-messages" ref={scrollRef}>
        {messages.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '48px 0', color: '#6b7280' }}>
            <Text type="secondary">提出你的数据问题，AI 将为你分析</Text>
            <div style={{ marginTop: 16 }}>
              {exampleQuestions.map((q, i) => (
                <Button key={i} size="small" style={{ margin: 4 }} onClick={() => setInput(q)}>
                  {q}
                </Button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((msg) => (
            <div key={msg.id} className={`message-item message-${msg.role}`}>
              {msg.role === 'user' ? (
                <div className="message-content">{msg.content}</div>
              ) : msg.proposal ? (
                <CleaningCard
                  proposal={msg.proposal}
                  onExecute={onExecuteCleaning || (() => {})}
                  onSaveAs={onSaveAsCleaning || (() => {})}
                  loading={loading}
                />
              ) : (
                <div className="message-content">
                  {msg.sql && <SqlBlock sql={msg.sql} />}
                  {msg.content ? (
                    <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>
                  ) : loading ? (
                    <Spin size="small" />
                  ) : null}
                </div>
              )}
              <div className="message-meta">
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {msg.role === 'user' ? '我' : 'AI'}
                </Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {formatTimestamp(msg.timestamp)}
                </Text>
              </div>
            </div>
          ))
        )}
      </div>

      <div className="chat-input">
        <Input.TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入你的问题，回车发送..."
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={loading}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={loading}
          style={{ marginTop: 8 }}
        >
          发送
        </Button>
      </div>
    </div>
  )
}

export default ChatPanel
