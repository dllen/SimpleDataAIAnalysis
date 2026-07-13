import React, { useState, useRef, useEffect } from 'react'
import { Layout, Upload, Button, message, Splitter } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { datasetApi } from '../api/dataset'
import { analysisApi } from '../api/analysis'
import { DatasetResponse, ChatMessage, QueryResult } from '../types'
import DatasetList from './DatasetList'
import DataPreview from '../components/DataPreview'
import ChatPanel from '../components/ChatPanel'

const { Header, Sider, Content } = Layout

const AnalysisWorkspace: React.FC = () => {
  const [selectedDataset, setSelectedDataset] = useState<DatasetResponse | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [loading, setLoading] = useState(false)
  const [streaming, setStreaming] = useState(false)

  const handleUpload = async (file: File) => {
    try {
      message.loading({ content: '上传中...', key: 'upload' })
      const dataset = await datasetApi.upload(file)
      message.success({ content: '上传成功', key: 'upload' })
      setSelectedDataset(dataset)
      setMessages([])
    } catch (e: any) {
      message.error({ content: e.response?.data?.message || '上传失败', key: 'upload' })
    }
    return false
  }

  const handleSelectDataset = (dataset: DatasetResponse) => {
    setSelectedDataset(dataset)
    setMessages([])
  }

  const handleSendMessage = async (question: string) => {
    if (!selectedDataset) {
      message.warning('请先选择数据集')
      return
    }

    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: question,
      timestamp: Date.now(),
    }
    setMessages((prev) => [...prev, userMsg])
    setLoading(true)
    setStreaming(true)

    const aiMsg: ChatMessage = {
      id: (Date.now() + 1).toString(),
      role: 'ai',
      content: '',
      timestamp: Date.now(),
    }
    setMessages((prev) => [...prev, aiMsg])

    try {
      await analysisApi.analyzeStream(selectedDataset.id, { question }, (eventType, data) => {
        setMessages((prev) => {
          const newMessages = [...prev]
          const lastMsg = newMessages[newMessages.length - 1]
          if (lastMsg.role !== 'ai') return prev

          switch (eventType) {
            case 'sql':
              lastMsg.sql = data
              break
            case 'token':
              lastMsg.content += data
              break
            case 'error':
              lastMsg.content = `错误: ${data}`
              break
            case 'done':
              break
          }
          return newMessages
        })

        if (eventType === 'done' || eventType === 'error') {
          setStreaming(false)
          setLoading(false)
        }
      })
    } catch (e: any) {
      setMessages((prev) => {
        const newMessages = [...prev]
        const lastMsg = newMessages[newMessages.length - 1]
        if (lastMsg.role === 'ai') {
          lastMsg.content = `分析失败: ${e.response?.data?.message || e.message}`
        }
        return newMessages
      })
      setStreaming(false)
      setLoading(false)
    }
  }

  return (
    <Layout style={{ height: '100vh' }}>
      <Header style={{ background: '#fff', padding: '0 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0 }}>数据分析 AI Agent</h2>
        <Upload
          beforeUpload={handleUpload}
          showUploadList={false}
          accept=".csv,.xlsx,.xls,.json"
        >
          <Button type="primary" icon={<UploadOutlined />}>
            上传数据文件
          </Button>
        </Upload>
      </Header>
      <Layout>
        <Sider width={280} style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}>
          <DatasetList onSelect={handleSelectDataset} selectedId={selectedDataset?.id} />
        </Sider>
        <Content style={{ background: '#f0f2f5', padding: 16 }}>
          {selectedDataset ? (
            <Splitter style={{ height: '100%', background: '#fff', borderRadius: 8, overflow: 'hidden' }}>
              <Splitter.Panel defaultSize="60%" min="30%" max="80%">
                <ChatPanel
                  messages={messages}
                  onSend={handleSendMessage}
                  loading={loading || streaming}
                />
              </Splitter.Panel>
              <Splitter.Panel>
                <DataPreview dataset={selectedDataset} />
              </Splitter.Panel>
            </Splitter>
          ) : (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', color: '#999' }}>
              <div style={{ textAlign: 'center' }}>
                <UploadOutlined style={{ fontSize: 48, marginBottom: 16 }} />
                <p>上传数据文件开始分析</p>
                <p style={{ fontSize: 12 }}>支持 CSV、Excel(.xlsx/.xls)、JSON 格式</p>
              </div>
            </div>
          )}
        </Content>
      </Layout>
    </Layout>
  )
}

export default AnalysisWorkspace
