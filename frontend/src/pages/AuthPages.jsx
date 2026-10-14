import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ErrorBox, Field } from '../components/ui.jsx'
import { STORE_NAME } from '../lib/format.js'
import { useAuth } from '../state/auth.jsx'
import { useToast } from '../state/toast.jsx'

const AUTH_IMAGE = 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=1200&q=80&auto=format&fit=crop'

function AuthShell({ title, subtitle, children }) {
  return (
    <div className="auth">
      <div className="auth-visual">
        <img src={AUTH_IMAGE} alt="" />
        <div className="auth-visual-text">
          <div className="logo logo-light">{STORE_NAME}</div>
          <p>Thời trang mỗi ngày – mua sắm dễ dàng, giao hàng tận nơi.</p>
        </div>
      </div>
      <div className="auth-panel">
        <div className="auth-card">
          <h1>{title}</h1>
          <p className="muted">{subtitle}</p>
          {children}
        </div>
      </div>
    </div>
  )
}

function useRedirectTarget() {
  const location = useLocation()
  const from = location.state?.from
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('/dang-') ? from : '/'
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const target = useRedirectTarget()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to={target} replace />

  const submit = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const u = await login(form.email, form.password)
      toast.success(`Xin chào, ${u.fullName}!`)
      navigate(target, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Đăng nhập" subtitle="Chào mừng bạn quay lại!">
      <form onSubmit={submit} className="stack" noValidate>
        <ErrorBox error={error} />
        <Field label="Email" error={error?.fieldErrors?.email}>
          <input type="email" value={form.email} autoComplete="email" required autoFocus
            onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Mật khẩu" error={error?.fieldErrors?.password}>
          <input type="password" value={form.password} autoComplete="current-password" required
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </Field>
        <button className="btn btn-primary btn-lg btn-block" disabled={submitting}>
          {submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>
      </form>
      <p className="center muted">
        Chưa có tài khoản? <Link to="/dang-ky" state={location.state} className="link">Đăng ký ngay</Link>
      </p>
    </AuthShell>
  )
}

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const target = useRedirectTarget()
  const location = useLocation()
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' })
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to={target} replace />

  const submit = async (e) => {
    e.preventDefault()
    if (form.password !== form.confirm) {
      setError({ message: 'Mật khẩu nhập lại không khớp', fieldErrors: { confirm: 'Mật khẩu nhập lại không khớp' } })
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await register(form.fullName, form.email, form.password)
      toast.success('Tạo tài khoản thành công!')
      navigate(target, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  const fe = error?.fieldErrors ?? {}
  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  return (
    <AuthShell title="Tạo tài khoản" subtitle="Đăng ký để đặt hàng và theo dõi đơn dễ dàng.">
      <form onSubmit={submit} className="stack" noValidate>
        <ErrorBox error={error && Object.keys(fe).length === 0 ? error : null} />
        <Field label="Họ và tên" error={fe.fullName}>
          <input value={form.fullName} onChange={set('fullName')} autoComplete="name" required maxLength={100} autoFocus />
        </Field>
        <Field label="Email" error={fe.email}>
          <input type="email" value={form.email} onChange={set('email')} autoComplete="email" required />
        </Field>
        <Field label="Mật khẩu" error={fe.password} hint="Ít nhất 8 ký tự">
          <input type="password" value={form.password} onChange={set('password')} autoComplete="new-password" required />
        </Field>
        <Field label="Nhập lại mật khẩu" error={fe.confirm}>
          <input type="password" value={form.confirm} onChange={set('confirm')} autoComplete="new-password" required />
        </Field>
        <button className="btn btn-primary btn-lg btn-block" disabled={submitting}>
          {submitting ? 'Đang tạo tài khoản...' : 'Đăng ký'}
        </button>
      </form>
      <p className="center muted">
        Đã có tài khoản? <Link to="/dang-nhap" state={location.state} className="link">Đăng nhập</Link>
      </p>
    </AuthShell>
  )
}
