import React, { useState } from 'react'
import { Layout, Upload, Button, message, Splitter, Tooltip, Typography, Tag } from 'antd'
import { UploadOutlined, ThunderboltOutlined, SaveOutlined } from '@ant-design/icons'
import { datasetApi } from '../api/dataset'
import { analysisApi } from '../api/analysis'
import { cleaningApi } from '../api/cleaning'
import { DatasetResponse, ChatMessage, DatasetStatus, CleaningExecutionRequest } from '../types'
import DatasetList from '../components/DatasetList'
import DataPreview from '../components/DataPreview'
import ChatPanel from '../components/ChatPanel'
import WorkspaceLayout from '../components/WorkspaceLayout'

const { Header, Sider, Content } = Layout
const { Text } = Typography

const AnalysisWorkspace: React.FC = () => {
  const [selectedDatasetId, setSelectedDatasetId] = useState<number | null>(null)
  const [selectedDataset, setSelectedDataset] = useState<DatasetResponse | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [loading, setLoading] = useState(false)
  const [streaming, setStreaming] = useState(false)
  const [cleaningLoading, setCleaningLoading] = useState(false)

  const refreshDataset = async (datasetId: number) => {
    const refreshed = await datasetApi.get(datasetId)
    setSelectedDataset(refreshed)
    return refreshed
  }

  const analyzeCleaning = async (datasetId: number) => {
    setCleaningLoading(true)
    try {
      const response = await cleaningApi.analyze(datasetId)
      const proposal = response.data
      const aiMsg: ChatMessage = {
        id: Date.now().toString(),
        role: 'ai',
        content: proposal.summary,
        proposal,
        timestamp: Date.now(),
      }
      setMessages((prev) => [...prev, aiMsg])
    } catch (e: any) {
      message.error(e.response?.data?.message || '检测失败')
    } finally {
      setCleaningLoading(false)
    }
  }

  const handleExecuteCleaning = async (request: CleaningExecutionRequest) => {
    if (!selectedDatasetId) return
    setCleaningLoading(true)
    try {
      await cleaningApi.execute(selectedDatasetId, request)
      message.success('清洗完成')
      const refreshed = await refreshDataset(selectedDatasetId)
      if (refreshed.status === DatasetStatus.PENDING_CLEAN) {
        await analyzeCleaning(refreshed.id)
      }
    } catch (e: any) {
      message.error(e.response?.data?.message || '清洗失败')
    } finally {
      setCleaningLoading(false)
    }
  }

  const handleSaveAsCleaning = async (request: CleaningExecutionRequest) => {
    if (!selectedDatasetId) return
    setCleaningLoading(true)
    try {
      const response = await cleaningApi.saveAs(selectedDatasetId, request)
      const newDataset = response.data
      message.success('已另存为新数据集')
      setSelectedDatasetId(newDataset.id)
      setSelectedDataset(newDataset)
      setMessages([])
    } catch (e: any) {
      message.error(e.response?.data?.message || '另存失败')
    } finally {
      setCleaningLoading(false)
    }
  }

  const handleUpload = async (file: File) => {
    try {
      message.loading({ content: '上传中...', key: 'upload' })
      const dataset = await datasetApi.upload(file)
      message.success({ content: '上传成功', key: 'upload' })
      setSelectedDatasetId(dataset.id)
      setSelectedDataset(dataset)
      setMessages([])

      if (dataset.status === DatasetStatus.PENDING_CLEAN) {
        await analyzeCleaning(dataset.id)
      }
    } catch (e: any) {
      message.error({ content: e.response?.data?.message || '上传失败', key: 'upload' })
    }
    return false
  }

  const handleSelectDataset = (dataset: DatasetResponse) => {
    if (!dataset?.id || dataset.id === selectedDatasetId) {
      return
    }
    setSelectedDatasetId(dataset.id)
    setSelectedDataset(dataset)
    setMessages([])
  }

  const handleSendMessage = async (question: string) => {
    if (!selectedDatasetId) {
      message.warning('请先选择数据集')
      return
    }

    const CLEANING_KEYWORDS = ['清洗', 'clean', '清理', '数据清洗']
    if (CLEANING_KEYWORDS.some((k) => question.toLowerCase().includes(k))) {
      await analyzeCleaning(selectedDatasetId)
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
      await analysisApi.analyzeStream(selectedDatasetId, { question }, (eventType, data) => {
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
    <WorkspaceLayout
      title="数据分析 AI Agent"
      dataset={selectedDataset}
      onUploadClick={() => document.getElementById('workspace-upload-input')?.click()}
    >
      <Layout style={{ height: '100%' }}>
        <Sider width={272} style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}>
          <DatasetList onSelect={handleSelectDataset} selectedId={selectedDatasetId} />
        </Sider>
        <Content style={{ background: '#f5f7fa', padding: 16 }}>
          {selectedDataset ? (
            <Splitter style={{ height: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden' }}>
              <Splitter.Panel defaultSize="58%" min="30%" max="78%">
                <ChatPanel
                  messages={messages}
                  onSend={handleSendMessage}
                  loading={loading || streaming || cleaningLoading}
                  onExecuteCleaning={handleExecuteCleaning}
                  onSaveAsCleaning={handleSaveAsCleaning}
                />
              </Splitter.Panel>
              <Splitter.Panel>
                <DataPreview dataset={selectedDataset} />
              </Splitter.Panel>
            </Splitter>
          ) : (
            <div className="workspace-empty">
              <div className="workspace-empty-inner">
                <UploadOutlined style={{ fontSize: 36, color: '#94a3b8' }} />
                <div className="workspace-title" style={{ marginTop: 12 }}>
                  上传数据文件，开始分析
                </div>
                <Text type="secondary" style={{ marginTop: 8 }}>
                  支持 CSV、Excel(.xlsx/.xls)、JSON 格式
                </Text>
                <div style={{ marginTop: 16 }}>
                  <Upload
                    id="workspace-upload-input"
                    beforeUpload={handleUpload}
                    showUploadList={false}
                    accept=".csv,.xlsx,.xls,.json"
                  >
                    <Button type="primary" icon={<UploadOutlined />}>
                      上传文件
                    </Button>
                  </Upload>
                </div>
              </div>
            </div>
          )}
        </Content>
      </Layout>
    </WorkspaceLayout>
  )
}

export default AnalysisWorkspace
