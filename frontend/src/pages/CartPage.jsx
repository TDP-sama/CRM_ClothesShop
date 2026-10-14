import { Link, useNavigate } from 'react-router-dom'
import Icon from '../components/Icon.jsx'
import { FreeShippingMeter } from '../components/Layout.jsx'
import { EmptyState, ProductImage, QuantityStepper } from '../components/ui.jsx'
import { formatPrice } from '../lib/format.js'
import { useCart } from '../state/cart.jsx'

export default function CartPage() {
  const cart = useCart()
  const navigate = useNavigate()

  if (cart.items.length === 0) {
    return (
      <div className="container page">
        <EmptyState icon="bag" title="Giỏ hàng của bạn đang trống"
          action={<Link to="/san-pham" className="btn btn-primary">Tiếp tục mua sắm</Link>}>
          Khám phá các sản phẩm mới nhất của chúng tôi.
        </EmptyState>
      </div>
    )
  }

  return (
    <div className="container page">
      <h1 className="page-title">Giỏ hàng</h1>
      <div className="two-col">
        <div className="card">
          <table className="cart-table">
            <thead>
              <tr>
                <th>Sản phẩm</th>
                <th>Đơn giá</th>
                <th>Số lượng</th>
                <th className="right">Thành tiền</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {cart.items.map((item) => (
                <tr key={item.variantId}>
                  <td>
                    <div className="cart-product">
                      <ProductImage src={item.imageUrl} alt={item.name} className="cart-img" />
                      <div>
                        <Link to={`/san-pham/${item.slug}`} className="strong">{item.name}</Link>
                        <div className="muted small">{item.color} / {item.size}</div>
                      </div>
                    </div>
                  </td>
                  <td data-label="Đơn giá">{formatPrice(item.price)}</td>
                  <td data-label="Số lượng">
                    <QuantityStepper size="sm" value={item.quantity} max={Math.min(20, item.stock)}
                      onChange={(q) => cart.setQuantity(item.variantId, q)} />
                  </td>
                  <td className="right strong" data-label="Thành tiền">{formatPrice(item.price * item.quantity)}</td>
                  <td className="right">
                    <button className="icon-btn icon-btn-sm" onClick={() => cart.remove(item.variantId)} aria-label="Xóa">
                      <Icon name="trash" size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <aside className="card summary">
          <h3>Tóm tắt đơn hàng</h3>
          <FreeShippingMeter remaining={cart.freeShippingThreshold - cart.subtotal}
            threshold={cart.freeShippingThreshold} subtotal={cart.subtotal} />
          <OrderTotals subtotal={cart.subtotal} shippingFee={cart.shippingFee} total={cart.total} />
          <button className="btn btn-primary btn-lg btn-block" onClick={() => navigate('/thanh-toan')}>Tiến hành thanh toán</button>
          <Link to="/san-pham" className="btn btn-ghost btn-block">Tiếp tục mua sắm</Link>
        </aside>
      </div>
    </div>
  )
}

export function OrderTotals({ subtotal, shippingFee, total }) {
  return (
    <div className="totals">
      <div className="summary-row"><span>Tạm tính</span><span>{formatPrice(subtotal)}</span></div>
      <div className="summary-row">
        <span>Phí vận chuyển</span>
        <span>{shippingFee === 0 ? <span className="text-success">Miễn phí</span> : formatPrice(shippingFee)}</span>
      </div>
      <div className="summary-row summary-total"><span>Tổng cộng</span><span>{formatPrice(total)}</span></div>
    </div>
  )
}
