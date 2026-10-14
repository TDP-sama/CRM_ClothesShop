import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import Icon from '../components/Icon.jsx'
import { ErrorBox, Field, ProductImage } from '../components/ui.jsx'
import { formatPrice } from '../lib/format.js'
import { useAuth } from '../state/auth.jsx'
import { useCart } from '../state/cart.jsx'
import { useToast } from '../state/toast.jsx'
import { OrderTotals } from './CartPage.jsx'

export default function Checkout() {
  const { user } = useAuth()
  const cart = useCart()
  const toast = useToast()
  const navigate = useNavigate()
  const [form, setForm] = useState({ recipientName: user?.fullName ?? '', phone: '', address: '', note: '' })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  if (cart.items.length === 0) return <Navigate to="/gio-hang" replace />

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const submit = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const order = await api.placeOrder({
        ...form,
        items: cart.items.map((i) => ({ variantId: i.variantId, quantity: i.quantity })),
      })
      cart.clear()
      toast.success('Đặt hàng thành công!')
      navigate(`/tai-khoan/don-hang/${order.id}`, { replace: true, state: { justPlaced: true } })
    } catch (err) {
      setError(err)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } finally {
      setSubmitting(false)
    }
  }

  const fe = error?.fieldErrors ?? {}

  return (
    <div className="container page">
      <nav className="breadcrumb">
        <Link to="/gio-hang">Giỏ hàng</Link> <span>/</span> <span>Thanh toán</span>
      </nav>
      <h1 className="page-title">Thanh toán</h1>
      <form className="two-col" onSubmit={submit} noValidate>
        <div className="stack">
          <ErrorBox error={error && Object.keys(fe).length === 0 ? error : null} />
          <section className="card">
            <h3>Thông tin giao hàng</h3>
            <div className="form-grid">
              <Field label="Họ và tên người nhận" error={fe.recipientName}>
                <input value={form.recipientName} onChange={set('recipientName')} autoComplete="name" required maxLength={100} />
              </Field>
              <Field label="Số điện thoại" error={fe.phone}>
                <input value={form.phone} onChange={set('phone')} autoComplete="tel" inputMode="tel" required placeholder="0901234567" />
              </Field>
              <Field label="Địa chỉ nhận hàng" error={fe.address} hint="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành phố">
                <textarea value={form.address} onChange={set('address')} autoComplete="street-address" rows={3} required maxLength={500} />
              </Field>
              <Field label="Ghi chú (không bắt buộc)" error={fe.note}>
                <textarea value={form.note} onChange={set('note')} rows={2} maxLength={500} placeholder="Ví dụ: giao giờ hành chính" />
              </Field>
            </div>
          </section>
          <section className="card">
            <h3>Phương thức thanh toán</h3>
            <label className="payment-option active">
              <input type="radio" checked readOnly />
              <Icon name="cash" size={22} />
              <div>
                <strong>Thanh toán khi nhận hàng (COD)</strong>
                <span className="muted small">Bạn chỉ trả tiền khi đã nhận và kiểm tra hàng.</span>
              </div>
            </label>
          </section>
        </div>

        <aside className="card summary">
          <h3>Đơn hàng ({cart.count} sản phẩm)</h3>
          <ul className="checkout-items">
            {cart.items.map((i) => (
              <li key={i.variantId}>
                <div className="checkout-thumb">
                  <ProductImage src={i.imageUrl} alt={i.name} />
                  <span className="qty-badge">{i.quantity}</span>
                </div>
                <div className="grow">
                  <div className="strong small">{i.name}</div>
                  <div className="muted small">{i.color} / {i.size}</div>
                </div>
                <span className="small">{formatPrice(i.price * i.quantity)}</span>
              </li>
            ))}
          </ul>
          <OrderTotals subtotal={cart.subtotal} shippingFee={cart.shippingFee} total={cart.total} />
          <button className="btn btn-primary btn-lg btn-block" disabled={submitting}>
            {submitting ? 'Đang đặt hàng...' : 'Đặt hàng'}
          </button>
          <p className="muted small center">Giá và tồn kho được xác nhận lại khi đặt hàng.</p>
        </aside>
      </form>
    </div>
  )
}
