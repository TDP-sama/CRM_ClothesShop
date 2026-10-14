import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
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

/** "Continue with Google": shown only when the backend has Google sign-in (Keycloak) configured. */
function GoogleSignIn() {
  const [enabled, setEnabled] = useState(false)
  useEffect(() => {
    api.authProviders().then((p) => setEnabled(Boolean(p?.google))).catch(() => setEnabled(false))
  }, [])
  if (!enabled) return null
  return (
    <>
      {/* A full page navigation: the OAuth2 flow redirects through Keycloak and Google, then back to the shop. */}
      <a href="/oauth2/authorization/keycloak" className="btn btn-outline btn-lg btn-block google-btn">
        <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
          <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
          <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
          <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z" />
          <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
        </svg>
        Tiếp tục với Google
      </a>
      <div className="divider"><span>hoặc</span></div>
    </>
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

  const googleFailed = new URLSearchParams(location.search).get('loi') === 'google'

  return (
    <AuthShell title="Đăng nhập" subtitle="Chào mừng bạn quay lại!">
      {googleFailed && !error && (
        <div className="alert alert-danger">Đăng nhập bằng Google không thành công, vui lòng thử lại.</div>
      )}
      <GoogleSignIn />
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
      <GoogleSignIn />
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
