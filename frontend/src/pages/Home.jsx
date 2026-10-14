import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'
import Icon from '../components/Icon.jsx'
import { ProductGrid } from '../components/ProductCard.jsx'
import { ProductImage } from '../components/ui.jsx'
import { formatPrice } from '../lib/format.js'
import { useCart } from '../state/cart.jsx'

const HERO_IMAGE = 'https://images.unsplash.com/photo-1445205170230-053b83016050?w=1800&q=80&auto=format&fit=crop'
const SALE_IMAGE = 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=1400&q=80&auto=format&fit=crop'

export default function Home() {
  const { freeShippingThreshold } = useCart()
  const [categories, setCategories] = useState(null)
  const [featured, setFeatured] = useState(null)
  const [latest, setLatest] = useState(null)

  useEffect(() => {
    api.categories().then(setCategories).catch(() => setCategories([]))
    api.products({ featured: true, size: 8 }).then((r) => setFeatured(r.items)).catch(() => setFeatured([]))
    api.products({ sort: 'newest', size: 4 }).then((r) => setLatest(r.items)).catch(() => setLatest([]))
  }, [])

  return (
    <>
      <section className="hero">
        <img src={HERO_IMAGE} alt="" className="hero-img" />
        <div className="hero-overlay" />
        <div className="container hero-content">
          <span className="eyebrow">Bộ sưu tập mới</span>
          <h1>Phong cách tinh gọn cho mỗi ngày</h1>
          <p>Chất liệu tốt, đường may chỉn chu, giá hợp lý. Giao hàng toàn quốc và thanh toán khi nhận hàng.</p>
          <div className="hero-actions">
            <Link to="/san-pham" className="btn btn-primary btn-lg">Mua sắm ngay</Link>
            <Link to="/san-pham?sale=true" className="btn btn-outline-light btn-lg">Xem ưu đãi</Link>
          </div>
        </div>
      </section>

      <section className="container section perks">
        <Perk icon="truck" title="Miễn phí vận chuyển" text={`Cho đơn hàng từ ${formatPrice(freeShippingThreshold)}`} />
        <Perk icon="cash" title="Thanh toán khi nhận" text="Kiểm tra hàng rồi mới trả tiền" />
        <Perk icon="shield" title="Cam kết chất lượng" text="Sản phẩm đúng mô tả, đúng hình ảnh" />
      </section>

      <section className="container section">
        <SectionHead title="Danh mục" link="/san-pham" linkText="Xem tất cả" />
        <div className="category-grid">
          {(categories ?? []).map((c) => (
            <Link key={c.id} to={`/san-pham?category=${c.slug}`} className="category-tile">
              <ProductImage src={c.imageUrl} alt={c.name} />
              <div className="category-tile-label">
                <strong>{c.name}</strong>
                <span>{c.productCount} sản phẩm</span>
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section className="container section">
        <SectionHead title="Nổi bật" link="/san-pham" linkText="Xem thêm" />
        <ProductGrid products={featured ?? []} loading={featured === null} />
      </section>

      <section className="container section">
        <div className="promo">
          <img src={SALE_IMAGE} alt="" />
          <div className="promo-content">
            <span className="eyebrow">Ưu đãi có hạn</span>
            <h2>Giá tốt cho những mẫu được yêu thích nhất</h2>
            <Link to="/san-pham?sale=true" className="btn btn-primary">
              Mua ngay <Icon name="chevronRight" size={16} />
            </Link>
          </div>
        </div>
      </section>

      <section className="container section">
        <SectionHead title="Hàng mới về" link="/san-pham?sort=newest" linkText="Xem thêm" />
        <ProductGrid products={latest ?? []} loading={latest === null} />
      </section>
    </>
  )
}

function SectionHead({ title, link, linkText }) {
  return (
    <div className="section-head">
      <h2>{title}</h2>
      {link && (
        <Link to={link} className="link-arrow">
          {linkText} <Icon name="chevronRight" size={16} />
        </Link>
      )}
    </div>
  )
}

function Perk({ icon, title, text }) {
  return (
    <div className="perk">
      <Icon name={icon} size={28} />
      <div>
        <strong>{title}</strong>
        <span className="muted small">{text}</span>
      </div>
    </div>
  )
}
