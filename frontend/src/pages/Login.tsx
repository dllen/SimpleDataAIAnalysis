import React, { useState } from 'react'
import { Form, Input, Button, Card, Tabs, message, Typography } from 'antd'
import { UserOutlined, LockOutlined, TableOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { useAuthStore } from '../store/authStore'
import { AuthRequest } from '../types'

const { Title, Text } = Typography

const Login: React.FC = () => {
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [loading, setLoading] = useState(false)

  const onLogin = async (values: AuthRequest) => {
    setLoading(true)
    try {
      const res = await authApi.login(values)
      setAuth(res.token, res.username, res.userId)
      message.success('登录成功')
      navigate('/workspace')
    } catch (e: any) {
      message.error(e.response?.data?.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  const onRegister = async (values: AuthRequest) => {
    setLoading(true)
    try {
      const res = await authApi.register(values)
      setAuth(res.token, res.username, res.userId)
      message.success('注册成功，欢迎使用数据分析平台')
      navigate('/workspace')
    } catch (e: any) {
      message.error(e.response?.data?.message || '注册失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-shell">
      <Card className="login-card" variant="outlined">
        <div className="login-brand" style={{ textAlign: 'center' }}>
          <TableOutlined style={{ fontSize: 32, color: '#1677ff' }} />
          <Title level={3} className="login-title" style={{ marginTop: 10 }}>
            数据分析 AI Agent
          </Title>
          <Text type="secondary" className="login-subtitle">
            上传表格数据，用自然语言快速完成分析
          </Text>
        </div>
        <Tabs
          items={[
            {
              key: 'login',
              label: '登录',
              children: (
                <Form onFinish={onLogin} layout="vertical">
                  <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
                    <Input prefix={<UserOutlined />} placeholder="请输入用户名" />
                  </Form.Item>
                  <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
                    <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" block loading={loading}>
                    登录
                  </Button>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    登录后可使用多用户数据隔离与 JWT 认证
                  </Text>
                </Form>
              ),
            },
            {
              key: 'register',
              label: '注册',
              children: (
                <Form onFinish={onRegister} layout="vertical">
                  <Form.Item name="username" rules={[{ required: true, min: 3, message: '用户名至少3个字符' }]}>
                    <Input prefix={<UserOutlined />} placeholder="请输入用户名" />
                  </Form.Item>
                  <Form.Item name="password" rules={[{ required: true, min: 6, message: '密码至少6个字符' }]}>
                    <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" block loading={loading}>
                    注册
                  </Button>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    注册即表示同意创建独立工作空间与上传隔离数据
                  </Text>
                </Form>
              ),
            },
          ]}
        />
        <div className="login-footer">
          如需体验多用户环境，可使用任意用户名完成注册
        </div>
      </Card>
    </div>
  )
}

export default Login
