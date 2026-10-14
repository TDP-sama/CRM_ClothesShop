import { useEffect, useState } from 'react'
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { formatPrice, STORE_NAME } from '../lib/format.js'
import { useAuth } from '../state/auth.jsx'
import { useCart } from '../state/cart.jsx'
import { THEMES, useTheme } from '../state/theme.jsx'
import { useToast } from '../state/toast.jsx'
import Icon from './Icon.jsx'
import { ProductImage, QuantityStepper, Spinner } from './ui.jsx'

export default function Layout() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return (
    <>
      <AnnouncementBar />
      <Header />
      <main className="main">
        <Outlet />
      </main>
      <Footer />
      <CartDrawer />
      <SettingsDrawer />
    </>
  )
}

function AnnouncementBar() {
  const { freeShippingThreshold } = useCart()
  return (
    <div className="announcement">
      Miễn phí vận chuyển cho đơn từ {formatPrice(freeShippingThreshold)} · Thanh toán khi nhận hàng
    </div>
  )
}

function Header() {
  const { user, isBackOffice, logout } = useAuth()
  const { count, openDrawer } = useCart()
  const { openSettings } = useTheme()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const [categories, setCategories] = useState([])
  const [menuOpen, setMenuOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [q, setQ] = useState('')

  useEffect(() => {
    api.categories().then(setCategories).catch(() => {})
  }, [])
  useEffect(() => {
    setMenuOpen(false)
    setSearchOpen(false)
  }, [location])

  const submitSearch = (e) => {
    e.preventDefault()
    if (q.trim()) navigate(`/san-pham?q=${encodeURIComponent(q.trim())}`)
  }

  const handleLogout = async () => {
    await logout()
    toast.success('Đã đăng xuất')
    navigate('/')
  }

  return (
    <header className="header">
      <div className="container header-inner">
        <button className="icon-btn only-mobile" onClick={() => setMenuOpen((o) => !o)} aria-label="Mở menu">
          <Icon name={menuOpen ? 'close' : 'menu'} />
        </button>
        <Link to="/" className="logo">{STORE_NAME}</Link>

        <nav className={`nav ${menuOpen ? 'nav-open' : ''}`}>
          <NavLink to="/san-pham" end className="nav-link">Tất cả</NavLink>
          {categories.map((c) => (
            <NavLink key={c.id} to={`/san-pham?category=${c.slug}`} className="nav-link">
              {c.name}
            </NavLink>
          ))}
          <NavLink to="/san-pham?sale=true" className="nav-link nav-sale">Giảm giá</NavLink>
          {isBackOffice && (
            <NavLink to="/quan-tri" className="nav-link nav-admin">
              <Icon name="dashboard" size={16} /> Quản trị
            </NavLink>
          )}
        </nav>

        <div className="header-actions">
          <form className={`search ${searchOpen ? 'search-open' : ''}`} onSubmit={submitSearch} role="search">
            <Icon name="search" size={18} />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Tìm sản phẩm..." aria-label="Tìm sản phẩm" />
          </form>
          <button className="icon-btn only-mobile" onClick={() => setSearchOpen((o) => !o)} aria-label="Tìm kiếm">
            <Icon name="search" />
          </button>
          <button className="icon-btn" onClick={openSettings} aria-label="Cài đặt giao diện" title="Cài đặt giao diện">
            <Icon name="settings" />
          </button>

          {user ? (
            <div className="account-menu">
              <button className="icon-btn" aria-label="Tài khoản">
                <Icon name="user" />
              </button>
              <div className="account-dropdown">
                <div className="account-name">
                  <strong>{user.fullName}</strong>
                  <span className="muted small">{user.email}</span>
                </div>
                <Link to="/tai-khoan/don-hang">
                  <Icon name="package" size={16} /> Đơn hàng của tôi
                </Link>
                {isBackOffice && (
                  <Link to="/quan-tri">
                    <Icon name="dashboard" size={16} /> Trang quản trị
                  </Link>
                )}
                <button onClick={handleLogout}>
                  <Icon name="logout" size={16} /> Đăng xuất
                </button>
              </div>
            </div>
          ) : (
            <Link to="/dang-nhap" state={{ from: location.pathname + location.search }} className="icon-btn" aria-label="Đăng nhập">
              <Icon name="user" />
            </Link>
          )}

          <button className="icon-btn cart-btn" onClick={openDrawer} aria-label={`Giỏ hàng, ${count} sản phẩm`}>
            <Icon name="bag" />
            {count > 0 && <span className="cart-count">{count}</span>}
          </button>
        </div>
      </div>
    </header>
  )
}

function Footer() {
  return (
    <footer className="footer">
      <div className="container footer-grid">
        <div>
          <div className="logo">{STORE_NAME}</div>
          <p className="muted">Thời trang mỗi ngày – chất liệu tốt, kiểu dáng tinh gọn, giá hợp lý.</p>
        </div>
        <div>
          <h4>Mua sắm</h4>
          <Link to="/san-pham">Tất cả sản phẩm</Link>
          <Link to="/san-pham?sale=true">Đang giảm giá</Link>
          <Link to="/san-pham?sort=newest">Hàng mới về</Link>
        </div>
        <div>
          <h4>Hỗ trợ</h4>
          <Link to="/tai-khoan/don-hang">Tra cứu đơn hàng</Link>
          <span className="muted">Thanh toán khi nhận hàng (COD)</span>
          <span className="muted">Giao hàng toàn quốc</span>
        </div>
      </div>
      <div className="container footer-bottom muted small">© {new Date().getFullYear()} {STORE_NAME}. Dự án luyện tập.</div>
    </footer>
  )
}

function Drawer({ open, onClose, title, children, footer, side = 'right' }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }, [open, onClose])
  return (
    <div className={`drawer-root ${open ? 'open' : ''}`} aria-hidden={!open}>
      <div className="drawer-backdrop" onClick={onClose} />
      <aside className={`drawer drawer-${side}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="drawer-head">
          <h3>{title}</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Đóng">
            <Icon name="close" />
          </button>
        </div>
        <div className="drawer-body">{children}</div>
        {footer && <div className="drawer-foot">{footer}</div>}
      </aside>
    </div>
  )
}

function CartDrawer() {
  const cart = useCart()
  const navigate = useNavigate()
  const remaining = cart.freeShippingThreshold - cart.subtotal
  const go = (path) => {
    cart.closeDrawer()
    navigate(path)
  }
  return (
    <Drawer
      open={cart.drawerOpen}
      onClose={cart.closeDrawer}
      title={`Giỏ hàng (${cart.count})`}
      footer={
        cart.items.length > 0 && (
          <>
            <div className="summary-row">
              <span>Tạm tính</span>
              <strong>{formatPrice(cart.subtotal)}</strong>
            </div>
            <button className="btn btn-primary btn-block" onClick={() => go('/thanh-toan')}>Thanh toán</button>
            <button className="btn btn-ghost btn-block" onClick={() => go('/gio-hang')}>Xem giỏ hàng</button>
          </>
        )
      }
    >
      {cart.items.length === 0 ? (
        <div className="empty">
          <div className="empty-icon"><Icon name="bag" size={32} /></div>
          <h3>Giỏ hàng trống</h3>
          <button className="btn btn-primary" onClick={() => go('/san-pham')}>Mua sắm ngay</button>
        </div>
      ) : (
        <>
          <FreeShippingMeter remaining={remaining} threshold={cart.freeShippingThreshold} subtotal={cart.subtotal} />
          <ul className="mini-cart">
            {cart.items.map((item) => (
              <li key={item.variantId}>
                <ProductImage src={item.imageUrl} alt={item.name} className="mini-cart-img" />
                <div className="mini-cart-info">
                  <Link to={`/san-pham/${item.slug}`} onClick={cart.closeDrawer} className="mini-cart-name">{item.name}</Link>
                  <span className="muted small">{item.color} / {item.size}</span>
                  <div className="mini-cart-row">
                    <QuantityStepper size="sm" value={item.quantity} max={Math.min(20, item.stock)}
                      onChange={(q) => cart.setQuantity(item.variantId, q)} />
                    <strong>{formatPrice(item.price * item.quantity)}</strong>
                  </div>
                </div>
                <button className="icon-btn icon-btn-sm" onClick={() => cart.remove(item.variantId)} aria-label="Xóa">
                  <Icon name="trash" size={16} />
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </Drawer>
  )
}

export function FreeShippingMeter({ remaining, threshold, subtotal }) {
  const pct = Math.min(100, Math.round((subtotal / threshold) * 100))
  return (
    <div className="ship-meter">
      <p className="small">
        {remaining > 0 ? (
          <>Mua thêm <strong>{formatPrice(remaining)}</strong> để được miễn phí vận chuyển</>
        ) : (
          <><Icon name="truck" size={16} /> Đơn hàng của bạn được <strong>miễn phí vận chuyển</strong></>
        )}
      </p>
      <div className="meter"><span style={{ width: `${pct}%` }} /></div>
    </div>
  )
}

function SettingsDrawer() {
  const { theme, setTheme, settingsOpen, closeSettings } = useTheme()
  return (
    <Drawer open={settingsOpen} onClose={closeSettings} title="Cài đặt giao diện">
      <p className="muted small">Chọn phong cách hiển thị cho cửa hàng. Lựa chọn được lưu trên trình duyệt này.</p>
      <div className="theme-list">
        {THEMES.map((t) => (
          <button
            key={t.id}
            className={`theme-option ${theme === t.id ? 'active' : ''}`}
            onClick={() => setTheme(t.id)}
            aria-pressed={theme === t.id}
          >
            <span className="theme-preview" style={{ background: t.preview.bg, borderColor: t.preview.surface }}>
              <span style={{ background: t.preview.surface }} />
              <span style={{ background: t.preview.text, width: '60%' }} />
              <span style={{ background: t.preview.accent, width: '35%' }} />
            </span>
            <span className="theme-text">
              <strong>{t.name}</strong>
              <span className="muted small">{t.description}</span>
            </span>
            {theme === t.id && <Icon name="check" className="theme-check" />}
          </button>
        ))}
      </div>
    </Drawer>
  )
}

/**
 * Sends visitors to the login page (and back afterwards). {@code backOffice} requires staff or admin,
 * {@code section} a granted admin section, {@code adminOnly} the ADMIN role. The server enforces the same rules;
 * this only keeps people away from pages that would fail.
 */
export function RequireAuth({ backOffice = false, section, adminOnly = false, children }) {
  const { user, loading, isAdmin, isBackOffice, can } = useAuth()
  const location = useLocation()
  if (loading) return <Spinner />
  if (!user) return <Navigate to="/dang-nhap" replace state={{ from: location.pathname + location.search }} />
  if ((backOffice || section || adminOnly) && !isBackOffice) return <Navigate to="/" replace />
  if ((adminOnly && !isAdmin) || (section && !can(section))) return <NoAccess />
  return children
}

function NoAccess() {
  return (
    <div className="empty">
      <div className="empty-icon"><Icon name="shield" size={32} /></div>
      <h3>Bạn không có quyền truy cập mục này</h3>
      <p className="muted">Hãy liên hệ quản trị viên nếu bạn cần được cấp quyền.</p>
    </div>
  )
}
