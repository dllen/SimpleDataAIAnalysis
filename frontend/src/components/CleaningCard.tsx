import React, { useState } from 'react'
import { Button, Card, Checkbox, Collapse, Input, Typography } from 'antd'
import { CleaningExecutionRequest, CleaningProposal } from '../types'

interface Props {
  proposal: CleaningProposal
  onExecute: (request: CleaningExecutionRequest) => void
  onSaveAs: (request: CleaningExecutionRequest) => void
  loading?: boolean
}

const { Text } = Typography
const { Panel } = Collapse

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

  return (
    <Card title="数据清洗建议" size="small" style={{ marginTop: 12 }}>
      <Text type="secondary">{proposal.summary}</Text>
      <div style={{ marginTop: 12 }}>
        {proposal.issues.map((issue, idx) => (
          <Card key={idx} size="small" style={{ marginBottom: 8 }}>
            <Checkbox checked={selected.has(idx)} onChange={() => toggleIssue(idx)}>
              <strong>{issue.type}</strong> - {issue.column}
            </Checkbox>
            <div style={{ marginTop: 4, color: '#666' }}>{issue.description}</div>
            <Collapse ghost size="small">
              <Panel header="建议操作" key="1">
                <div>{issue.suggestion}</div>
              </Panel>
              <Panel header="SQL（可编辑）" key="2">
                <Input.TextArea
                  defaultValue={issue.defaultSql}
                  onChange={(e) => setCustomSqls({ ...customSqls, [idx]: e.target.value })}
                  rows={3}
                  style={{ fontFamily: 'monospace' }}
                />
              </Panel>
            </Collapse>
          </Card>
        ))}
      </div>
      <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
        <Button type="primary" loading={loading} onClick={() => onExecute(buildRequest())}>
          执行清洗
        </Button>
        <Input
          placeholder="新数据集名称"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          style={{ width: 180 }}
        />
        <Button loading={loading} onClick={() => onSaveAs({ ...buildRequest(), saveAsNewDataset: true, newDatasetName: newName })}>
          另存为新数据集
        </Button>
      </div>
    </Card>
  )
}