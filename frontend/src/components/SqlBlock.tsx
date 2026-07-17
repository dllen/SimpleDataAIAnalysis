import React from 'react'
import { Tooltip, message, Typography, Button } from 'antd'
import { CodeOutlined, CopyOutlined } from '@ant-design/icons'

const { Text } = Typography

interface Props {
  sql: string
}

const SqlBlock: React.FC<Props> = ({ sql }) => {
  const handleCopy = () => {
    navigator.clipboard.writeText(sql)
    message.success('SQL 已复制')
  }

  return (
    <div className="sql-block" style={{ position: 'relative' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
        <Text style={{ fontSize: 12, color: '#94a3b8' }}>
          <CodeOutlined /> 生成的 SQL
        </Text>
        <Tooltip title="复制 SQL">
          <Button type="text" size="small" icon={<CopyOutlined />} onClick={handleCopy}>
            复制
          </Button>
        </Tooltip>
      </div>
      <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
        <Text style={{ color: '#e2e8f0' }}>{sql}</Text>
      </pre>
    </div>
  )
}

export default SqlBlock
