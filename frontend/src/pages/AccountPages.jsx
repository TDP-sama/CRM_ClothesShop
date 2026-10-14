import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { api } from '../api.js'
import Icon from '../components/Icon.jsx'
import { EmptyState, ErrorBox, Pagination, ProductImage, Spinner, StatusBadge } from '../components/ui.jsx'
import { formatDateTime, formatPrice, ORDER_FLOW } from '../lib/format.js'
import { useToast } from '../state/toast.jsx'
import { OrderTotals } from './CartPage.jsx'

export function OrdersPage() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    setData(null)
    api.myOrders(page).then(setData).catch(setError)
  }, [page])

  return (
    <div className="container page narrow">
      <h1 className="page-title">Đơn hàng của tôi</h1>
      <ErrorBox error={error} />
      {!data && !error && <Spinner />}
      {data && data.items.length === 0 && (
        <EmptyState icon="package" title="Bạn chưa có đơn hàng nào"
          action={<Link to="/san-pham" className="btn btn-primary">Bắt đầu mua sắm</Link>} />
      )}
      <div className="stack">
        {data?.items.map((o) => (
          <Link key={o.id} to={`/tai-khoan/don-hang/${o.id}`} className="card order-card">
            <div className="order-card-head">
              <div>
                <strong>{o.code}</strong>
                <span className="muted small"> · {formatDateTime(o.createdAt)}</span>
              </div>
              <StatusBadge status={o.status} />
            </div>
            <div className="order-card-items">
              {o.items.slice(0, 4).map((i) => (
                <ProductImage key={`${i.productId}-${i.size}-${i.color}`} src={i.imageUrl} alt={i.productName} className="order-thumb" />
              ))}
              {o.items.length > 4 && <span className="order-thumb more">+{o.items.length - 4}</span>}
            </div>
            <div className="order-card-foot">
              <span className="muted small">{o.itemCount} sản phẩm</span>
              <span>Tổng: <strong>{formatPrice(o.total)}</strong></span>
            </div>
          </Link>
        ))}
      </div>
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  )
}

export function OrderDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const toast = useToast()
  const [order, setOrder] = useState(null)
  const [error, setError] = useState(null)
  const [cancelling, setCancelling] = useState(false)

  useEffect(() => {
    api.myOrder(id).then(setOrder).catch(setError)
  }, [id])

  const cancel = async () => {
    if (!window.confirm('Bạn chắc chắn muốn hủy đơn hàng này?')) return
    setCancelling(true)
    try {
      setOrder(await api.cancelOrder(id))
      toast.success('Đã hủy đơn hàng')
    } catch (err) {
      toast.error(err.message)
    } finally {
      setCancelling(false)
    }
  }

  if (error) return <div className="container page narrow"><ErrorBox error={error} /></div>
  if (!order) return <Spinner />

  return (
    <div className="container page narrow">
      <Link to="/tai-khoan/don-hang" className="link-back"><Icon name="arrowLeft" size={16} /> Tất cả đơn hàng</Link>
      {location.state?.justPlaced && (
        <div className="alert alert-success success-banner">
          <Icon name="check" size={22} />
          <div>
            <strong>Cảm ơn bạn đã đặt hàng!</strong>
            <div className="small">Chúng tôi sẽ liên hệ để xác nhận đơn hàng sớm nhất.</div>
          </div>
        </div>
      )}
      <div className="order-detail-head">
        <div>
          <h1 className="page-title">Đơn hàng {order.code}</h1>
          <p className="muted">Đặt lúc {formatDateTime(order.createdAt)}</p>
        </div>
        <StatusBadge status={order.status} />
      </div>

      <OrderTimeline status={order.status} />

      <div className="two-col">
        <section className="card">
          <h3>Sản phẩm</h3>
          <OrderItems items={order.items} />
          <OrderTotals subtotal={order.subtotal} shippingFee={order.shippingFee} total={order.total} />
        </section>
        <aside className="stack">
          <section className="card">
            <h3>Giao đến</h3>
            <p className="strong">{order.recipientName}</p>
            <p className="muted">{order.phone}</p>
            <p>{order.address}</p>
            {order.note && <p className="muted small">Ghi chú: {order.note}</p>}
          </section>
          <section className="card">
            <h3>Thanh toán</h3>
            <p>Thanh toán khi nhận hàng (COD)</p>
          </section>
          {order.cancellable && (
            <button className="btn btn-danger-outline btn-block" onClick={cancel} disabled={cancelling}>
              {cancelling ? 'Đang hủy...' : 'Hủy đơn hàng'}
            </button>
          )}
        </aside>
      </div>
    </div>
  )
}

export function OrderTimeline({ status }) {
  if (status === 'CANCELLED') {
    return <div className="alert alert-danger">Đơn hàng đã bị hủy.</div>
  }
  const current = ORDER_FLOW.indexOf(status)
  const labels = ['Đặt hàng', 'Xác nhận', 'Đang giao', 'Đã giao']
  return (
    <ol className="timeline">
      {labels.map((label, i) => (
        <li key={label} className={i <= current ? 'done' : ''}>
          <span className="timeline-dot">{i <= current ? <Icon name="check" size={14} /> : i + 1}</span>
          <span className="small">{label}</span>
        </li>
      ))}
    </ol>
  )
}

export function OrderItems({ items }) {
  return (
    <ul className="order-items">
      {items.map((i) => (
        <li key={`${i.productId}-${i.size}-${i.color}`}>
          <ProductImage src={i.imageUrl} alt={i.productName} className="order-thumb" />
          <div className="grow">
            <Link to={`/san-pham/${i.productSlug}`} className="strong">{i.productName}</Link>
            <div className="muted small">{i.color} / {i.size} · x{i.quantity}</div>
          </div>
          <span>{formatPrice(i.lineTotal)}</span>
        </li>
      ))}
    </ul>
  )
}

export function NotFound() {
  return (
    <div className="container page">
      <EmptyState icon="search" title="Không tìm thấy trang"
        action={<Link to="/" className="btn btn-primary">Về trang chủ</Link>}>
        Đường dẫn có thể đã thay đổi hoặc không tồn tại.
      </EmptyState>
    </div>
  )
}
