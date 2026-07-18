import React, { useEffect, useState } from 'react'
import { Card, Button, Tag, Tooltip, Typography, Empty, Spin } from 'antd'
import { DeleteOutlined, DatabaseOutlined, FileExcelOutlined, FileTextOutlined, SyncOutlined } from '@ant-design/icons'
import { datasetApi } from '../api/dataset'
import { DatasetResponse } from '../types'

interface Props {
  onSelect: (dataset: DatasetResponse) => void
  selectedId?: number
}

const FILE_TYPE_META: Record<string, { icon: React.ReactNode; label: string; color: string }> = {
  csv: { icon: <FileTextOutlined style={{ color: '#52c41a' }} />, label: 'CSV', color: 'green' },
  xlsx: { icon: <FileExcelOutlined style={{ color: '#1890ff' }} />, label: 'Excel', color: 'blue' },
  xls: { icon: <FileExcelOutlined style={{ color: '#1890ff' }} />, label: 'Excel', color: 'blue' },
  json: { icon: <DatabaseOutlined style={{ color: '#faad14' }} />, label: 'JSON', color: 'orange' },
}

const DatasetList: React.FC<Props> = ({ onSelect, selectedId }) => {
  const [datasets, setDatasets] = useState<DatasetResponse[]>([])
  const [loading, setLoading] = useState(false)

  const loadDatasets = async () => {
    setLoading(true)
    try {
      const data = await datasetApi.list()
      setDatasets(data)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadDatasets()
  }, [])

  const handleDelete = async (id: number) => {
    await datasetApi.delete(id)
    loadDatasets()
    if (selectedId === id) {
      onSelect({} as DatasetResponse)
    }
  }

  return (
    <Card
      className="dataset-list-card"
      size="small"
      styles={{ body: { padding: 12, height: 'calc(100% - 44px)', overflow: 'hidden' } }}
    >
      <div className="dataset-list-toolbar">
        <Typography.Text strong>数据集</Typography.Text>
        <div style={{ display: 'flex', gap: 6 }}>
          <Button size="small" onClick={loadDatasets} icon={<SyncOutlined spin={loading} />}>
            刷新
          </Button>
        </div>
      </div>
      <Spin spinning={loading}>
        {datasets.length === 0 ? (
          <div className="dataset-empty">
            <DatabaseOutlined style={{ fontSize: 24, color: '#cbd5e1' }} />
            <div style={{ marginTop: 10, color: '#94a3b8' }}>暂无数据集</div>
            <div style={{ marginTop: 4, fontSize: 12 }}>上传文件后，列表会自动更新</div>
          </div>
        ) : (
          <div className="dataset-list-scroll">
            {datasets.map((item) => {
              const meta = FILE_TYPE_META[item.fileType] || FILE_TYPE_META.csv
              const active = selectedId === item.id

              return (
                <div
                  key={item.id}
                  className={`dataset-item ${active ? 'is-selected' : ''}`}
                  onClick={() => onSelect(item)}
                >
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div className="dataset-item-title">{item.fileName}</div>
                    <div className="dataset-item-meta">
                      <Tag bordered={false} color={meta.color} icon={meta.icon} style={{ margin: 0 }}>
                        {meta.label}
                      </Tag>
                      <span>{item.rowCount?.toLocaleString?.() ?? item.rowCount} 行</span>
                      <span style={{ color: '#cbd5e1' }}>·</span>
                      <span>{item.columns?.length || 0} 列</span>
                    </div>
                  </div>
                  <Tooltip title="删除数据集">
                    <Button
                      type="text"
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                      onClick={(event) => {
                        event.stopPropagation()
                        handleDelete(item.id)
                      }}
                    />
                  </Tooltip>
                </div>
              )
            })}
          </div>
        )}
      </Spin>
    </Card>
  )
}

export default DatasetList
