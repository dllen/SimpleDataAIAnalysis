import React, { useEffect, useState } from 'react'
import { Card, List, Tag, Button, Popconfirm, Empty, Spin } from 'antd'
import { DeleteOutlined, DatabaseOutlined, FileExcelOutlined, FileTextOutlined } from '@ant-design/icons'
import { datasetApi } from '../api/dataset'
import { DatasetResponse } from '../types'

interface Props {
  onSelect: (dataset: DatasetResponse) => void
  selectedId?: number
}

const fileIcons: Record<string, React.ReactNode> = {
  csv: <FileTextOutlined style={{ color: '#52c41a' }} />,
  xlsx: <FileExcelOutlined style={{ color: '#1890ff' }} />,
  xls: <FileExcelOutlined style={{ color: '#1890ff' }} />,
  json: <DatabaseOutlined style={{ color: '#faad14' }} />,
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
  }

  return (
    <Card
      title="数据集"
      extra={<Button size="small" onClick={loadDatasets}>刷新</Button>}
      styles={{ body: { padding: 0, overflow: 'auto', height: 'calc(100% - 57px)' } }}
    >
      <Spin spinning={loading}>
        {datasets.length === 0 ? (
          <Empty description="暂无数据集，请先上传文件" style={{ padding: 32 }} />
        ) : (
          <List
            dataSource={datasets}
            renderItem={(item) => (
              <List.Item
                style={{
                  padding: '12px 16px',
                  cursor: 'pointer',
                  backgroundColor: selectedId === item.id ? '#e6f7ff' : undefined,
                  borderLeft: selectedId === item.id ? '3px solid #1677ff' : '3px solid transparent',
                }}
                onClick={() => onSelect(item)}
                actions={[
                  <Popconfirm title="确定删除？" onConfirm={() => handleDelete(item.id)}>
                    <Button type="text" danger icon={<DeleteOutlined />} />
                  </Popconfirm>,
                ]}
              >
                <List.Item.Meta
                  avatar={fileIcons[item.fileType] || <FileTextOutlined />}
                  title={item.fileName}
                  description={
                    <div>
                      <Tag color="blue">{item.fileType.toUpperCase()}</Tag>
                      <span style={{ fontSize: 12, color: '#888' }}>
                        {item.rowCount} 行 | {item.columns?.length || 0} 列
                      </span>
                    </div>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Spin>
    </Card>
  )
}

export default DatasetList
