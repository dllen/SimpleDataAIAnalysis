import React, { useState } from 'react'
import { Button, Card, Checkbox, Collapse, Input, Typography, Tag, Tooltip } from 'antd'
import { ThunderboltOutlined, SaveOutlined } from '@ant-design/icons'
import { CleaningExecutionRequest, CleaningProposal } from '../types'

const { Text, Title } = Typography
const { Panel } = Collapse

interface Props {
  proposal: CleaningProposal
  onExecute: (request: CleaningExecutionRequest) => void
  onSaveAs: (request: CleaningExecutionRequest) => void
  loading?: boolean
}

export const CleaningCard: React.FC<Props> = ({ proposal, onExecute, onSaveAs, loading }) => {
  const [selected, setSelected] = useState<Set<number>>(new Set(proposal.issues.map((_, i) => i)))
  const [customSqls, setCustomSqls] = useState<Record<number, string>>({})
  const [newName, setNewName] = useState('')

  const toggleIssue = (idx: number) => {
    const next = new Set(selected)
    if (next.has(idx)) next.delete(idx)
    else next.add(idx)
    setSelected(next)
  }

  const buildRequest = (): CleaningExecutionRequest => ({
    selectedIssueIndexes: Array.from(selected),
    customSqls: proposal.issues.map((_, i) => customSqls[i] || ''),
    saveAsNewDataset: false,
  })

  const totalSelected = selected.size

  return (
    <Card className="cleaning-card" size="small" style={{ marginTop: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
        <div>
          <Title level={5} style={{ marginBottom: 4 }}>
            数据清洗建议
          </Title>
          <Text type="secondary">{proposal.summary}</Text>
        </div>
        <Tag color="orange">共 {proposal.issues.length} 个问题</Tag>
      </div>
      <div style={{ marginTop: 12 }}>
        {proposal.issues.map((issue, idx) => (
          <div key={idx} className="cleaning-issue">
            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
              <Checkbox checked={selected.has(idx)} onChange={() => toggleIssue(idx)} style={{ marginTop: 4 }}>
                <div>
                  <Text strong>
                    {issue.type} · {issue.column}
                  </Text>
                  <div style={{ color: '#4b5563' }}>{issue.description}</div>
                </div>
              </Checkbox>
              <Tag color="orange">{issue.affectedRows} 行</Tag>
            </div>
            <Collapse ghost size="small" style={{ marginTop: 8 }}>
              <Panel header="建议操作" key="suggestion">
                <div>{issue.suggestion}</div>
              </Panel>
              <Panel header="可编辑 SQL" key="sql">
                <Input.TextArea
                  defaultValue={issue.defaultSql}
                  onChange={(e) => setCustomSqls({ ...customSqls, [idx]: e.target.value })}
                  rows={3}
                  style={{ fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace' }}
                />
              </Panel>
            </Collapse>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 14, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <Tooltip title={totalSelected === 0 ? '请至少选择一个清洗项' : `已选 ${totalSelected} 项`}>
          <Button
            type="primary"
            icon={<ThunderboltOutlined />}
            loading={loading}
            disabled={totalSelected === 0}
            onClick={() => onExecute(buildRequest())}
          >
            执行清洗
          </Button>
        </Tooltip>
        <Input
          placeholder="新数据集名称"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          style={{ width: 220 }}
        />
        <Button
          icon={<SaveOutlined />}
          loading={loading}
          onClick={() => onSaveAs({ ...buildRequest(), saveAsNewDataset: true, newDatasetName: newName })}
        >
          另存为新数据集
        </Button>
      </div>
    </Card>
  )
}
