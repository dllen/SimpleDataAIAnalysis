import React, { useEffect, useState } from 'react'
import { Card, Table, Tag, Spin, Empty, Tabs } from 'antd'
import { datasetApi } from '../api/dataset'
import { DatasetResponse, QueryResult, ColumnInfo } from '../types'
import ReactECharts from 'echarts-for-react'

interface Props {
  dataset: DatasetResponse
}

const DataPreview: React.FC<Props> = ({ dataset }) => {
  const [preview, setPreview] = useState<QueryResult | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    datasetApi.preview(dataset.id).then((data) => {
      setPreview(data)
      setLoading(false)
    }).catch(() => setLoading(false))
  }, [dataset.id])

  const numericColumns = dataset.columns?.filter((c) =>
    ['BIGINT', 'INTEGER', 'DOUBLE', 'DECIMAL', 'FLOAT', 'REAL'].some((t) => c.type.toUpperCase().includes(t))
  ) || []

  const categoricalColumns = dataset.columns?.filter((c) =>
    ['VARCHAR', 'TEXT', 'STRING'].some((t) => c.type.toUpperCase().includes(t))
  ) || []

  const getChartOption = (): echarts.EChartsOption | null => {
    if (!preview || numericColumns.length === 0) return null

    const catCol = categoricalColumns[0]?.name || preview.columns[0]
    const numCol = numericColumns[0]?.name || preview.columns[1]
    const catIdx = preview.columns.indexOf(catCol)
    const numIdx = preview.columns.indexOf(numCol)

    if (catIdx < 0 || numIdx < 0) return null

    const chartData = preview.rows.slice(0, 20).map((row) => ({
      name: String(row[catIdx]),
      value: Number(row[numIdx]) || 0,
    }))

    const uniqueCategories = new Set(chartData.map((d) => d.name))
    const chartType = uniqueCategories.size <= 10 ? 'pie' : 'bar'

    if (chartType === 'pie') {
      return {
        title: { text: `${catCol} - ${numCol} 分布`, left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        series: [{
          type: 'pie',
          radius: ['40%', '70%'],
          data: chartData,
          emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
        }],
      }
    }

    return {
      title: { text: `${catCol} - ${numCol}`, left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: chartData.map((d) => d.name), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: chartData.map((d) => d.value), itemStyle: { color: '#1677ff' } }],
      grid: { left: 50, right: 20, bottom: 60, top: 50 },
    }
  }

  const tableColumns = preview?.columns.map((col) => ({
    title: col,
    dataIndex: col,
    key: col,
    ellipsis: true,
    width: 120,
  })) || []

  const tableData = preview?.rows.map((row, idx) => {
    const record: Record<string, any> = { key: idx }
    preview.columns.forEach((col, i) => {
      record[col] = row[i]
    })
    return record
  }) || []

  const chartOption = getChartOption()

  const tabItems = [
    {
      key: 'data',
      label: '数据预览',
      children: (
        <Spin spinning={loading}>
          {preview ? (
            <div className="data-table-wrapper">
              <Table
                columns={tableColumns}
                dataSource={tableData}
                size="small"
                pagination={{ pageSize: 10, showSizeChanger: false }}
                scroll={{ x: 'max-content' }}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: '#888' }}>
                共 {preview.totalRows} 行 (最多显示前100行)，查询耗时 {preview.executionTimeMs}ms
              </div>
            </div>
          ) : (
            <Empty description="暂无数据" />
          )}
        </Spin>
      ),
    },
  ]

  if (chartOption) {
    tabItems.push({
      key: 'chart',
      label: '可视化',
      children: (
        <ReactECharts option={chartOption} style={{ height: '100%', minHeight: 300 }} />
      ),
    })
  }

  tabItems.push({
    key: 'schema',
    label: '表结构',
    children: (
      <Table
        columns={[
          { title: '列名', dataIndex: 'name', key: 'name' },
          {
            title: '类型',
            dataIndex: 'type',
            key: 'type',
            render: (type) => <Tag color="blue">{type}</Tag>,
          },
          {
            title: '可空',
            dataIndex: 'nullable',
            key: 'nullable',
            render: (v) => v ? <Tag color="green">是</Tag> : <Tag color="red">否</Tag>,
          },
        ]}
        dataSource={dataset.columns?.map((c, i) => ({ ...c, key: i })) || []}
        size="small"
        pagination={false}
      />
    ),
  })

  return (
    <Card
      title={`数据预览: ${dataset.fileName}`}
      styles={{ body: { height: 'calc(100% - 57px)', overflow: 'auto' } }}
    >
      <Tabs items={tabItems} size="small" />
    </Card>
  )
}

export default DataPreview
