import React from 'react'
import { Tooltip, message } from 'antd'
import { CodeOutlined, CopyOutlined } from '@ant-design/icons'

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
        <span style={{ fontSize: 11, color: '#666' }}>
          <CodeOutlined /> 生成的 SQL
        </span>
        <Tooltip title="复制 SQL">
          <CopyOutlined
            style={{ cursor: 'pointer', color: '#999' }}
            onClick={handleCopy}
          />
        </Tooltip>
      </div>
      <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{sql}</pre>
    </div>
  )
}

export default SqlBlock
