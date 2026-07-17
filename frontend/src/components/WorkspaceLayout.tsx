import React from 'react'
import { Layout, Button, Dropdown, Tag, Tooltip, Typography } from 'antd'
import {
  DatabaseOutlined,
  FileTextOutlined,
  FileExcelOutlined,
  DatabaseFilled,
  SyncOutlined,
  UserOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '../store/authStore'
import { DatasetResponse, DatasetStatus } from '../types'

const { Text } = Typography

const FILE_TYPE_META: Record<string, { icon: React.ReactNode; label: string; color: string }> = {
  csv: { icon: <FileTextOutlined />, label: 'CSV', color: 'green' },
  xlsx: { icon: <FileExcelOutlined />, label: 'Excel', color: 'blue' },
  xls: { icon: <FileExcelOutlined />, label: 'Excel', color: 'blue' },
  json: { icon: <DatabaseOutlined />, label: 'JSON', color: 'orange' },
}

const STATUS_META: Record<string, { label: string; color: string }> = {
  [DatasetStatus.READY]: { label: '就绪', color: 'green' },
  [DatasetStatus.PENDING_CLEAN]: { label: '待清洗', color: 'orange' },
  [DatasetStatus.CLEANED]: { label: '已清洗', color: 'cyan' },
  [DatasetStatus.FAILED]: { label: '失败', color: 'red' },
}

interface WorkspaceLayoutProps {
  title?: React.ReactNode
  dataset?: DatasetResponse | null
  onReloadDatasets?: () => void
  onUploadClick?: () => void
  children: React.ReactNode
}

const WorkspaceLayout: React.FC<WorkspaceLayoutProps> = ({
  title = '数据分析 AI Agent',
  dataset,
  onReloadDatasets,
  onUploadClick,
  children,
}) => {
  const username = useAuthStore((state) => state.username)

  const fileMeta = dataset ? FILE_TYPE_META[dataset.fileType] || FILE_TYPE_META.csv : null
  const statusMeta = dataset ? STATUS_META[dataset.status] || null : null

  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      danger: true,
    },
  ]

  const handleUserMenuClick = () => {
    useAuthStore.getState().clearAuth()
    window.location.href = '/login'
  }

  return (
    <div className="workspace-shell">
      <header className="app-header">
        <div className="app-header-left">
          <Tooltip title="返回工作台">
            <div className="app-header-brand">
              <DatabaseFilled style={{ color: '#1677ff' }} />
              {title}
            </div>
          </Tooltip>
          {dataset ? (
            <div className="app-header-dataset">
              <Tag icon={fileMeta?.icon} color={fileMeta?.color}>
                {fileMeta?.label || dataset.fileType.toUpperCase()}
              </Tag>
              <Tooltip title={dataset.fileName}>
                <span className="app-header-dataset-title">{dataset.fileName}</span>
              </Tooltip>
              {statusMeta ? (
                <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
              ) : null}
              <Text type="secondary" style={{ fontSize: 12 }}>
                {dataset.rowCount?.toLocaleString?.() ?? dataset.rowCount} 行 · {dataset.columns?.length || 0} 列
              </Text>
            </div>
          ) : null}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {onReloadDatasets ? (
            <Tooltip title="刷新数据集列表">
              <Button
                size="small"
                type="text"
                icon={<SyncOutlined spin />}
                onClick={onReloadDatasets}
              />
            </Tooltip>
          ) : null}
          {onUploadClick ? (
            <Button size="small" type="primary" onClick={onUploadClick}>
              上传文件
            </Button>
          ) : null}
          <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }} placement="bottomRight">
            <Button size="small" type="text" icon={<UserOutlined />}>
              {username || '用户'}
            </Button>
          </Dropdown>
        </div>
      </header>
      <main className="workspace-content">{children}</main>
    </div>
  )
}

export default WorkspaceLayout
