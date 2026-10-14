import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api.js'
import Icon from '../components/Icon.jsx'
import { ProductGrid } from '../components/ProductCard.jsx'
import { EmptyState, ErrorBox, Price, ProductImage, QuantityStepper, Spinner, StarInput, Stars } from '../components/ui.jsx'
import { formatDate, formatPrice, swatchColor } from '../lib/format.js'
import { useAuth } from '../state/auth.jsx'
import { useCart } from '../state/cart.jsx'
import { useToast } from '../state/toast.jsx'

export default function ProductPage() {
  const { slug } = useParams()
  const navigate = useNavigate()
  const cart = useCart()
  const toast = useToast()
  const [product, setProduct] = useState(null)
  const [error, setError] = useState(null)
  const [related, setRelated] = useState([])
  const [imageIndex, setImageIndex] = useState(0)
  const [color, setColor] = useState(null)
  const [size, setSize] = useState(null)
  const [quantity, setQuantity] = useState(1)

  useEffect(() => {
    setProduct(null)
    setError(null)
    setImageIndex(0)
    setSize(null)
    setQuantity(1)
    api
      .product(slug)
      .then((p) => {
        setProduct(p)
        // Preselect the first color that still has stock.
        const firstAvailable = p.variants.find((v) => v.stock > 0) ?? p.variants[0]
        setColor(firstAvailable?.color ?? null)
        if (p.sizes.length === 1) setSize(p.sizes[0])
      })
      .catch(setError)
    api.related(slug).then(setRelated).catch(() => setRelated([]))
  }, [slug])

  if (error) {
    return (
      <div className="container page">
        {error.status === 404 ? (
          <EmptyState icon="search" title="Sản phẩm không tồn tại hoặc đã ngừng bán"
            action={<Link to="/san-pham" className="btn btn-primary">Xem sản phẩm khác</Link>} />
        ) : (
          <ErrorBox error={error} />
        )}
      </div>
    )
  }
  if (!product) return <Spinner />

  const variantFor = (s, c) => product.variants.find((v) => v.size === s && v.color === c)
  const selected = size && color ? variantFor(size, color) : null
  const maxQty = Math.min(20, selected?.stock ?? 0)
  const images = product.images.length ? product.images : [null]

  const addToCart = (goToCheckout) => {
    if (!selected) {
      toast.error('Vui lòng chọn size')
      return
    }
    cart.add(
      {
        variantId: selected.id,
        slug: product.slug,
        name: product.name,
        imageUrl: product.images[0] ?? null,
        size: selected.size,
        color: selected.color,
        price: product.price,
        stock: selected.stock,
      },
      quantity,
    )
    if (goToCheckout) navigate('/thanh-toan')
    else {
      toast.success('Đã thêm vào giỏ hàng')
      cart.openDrawer()
    }
  }

  return (
    <div className="container page">
      <nav className="breadcrumb">
        <Link to="/">Trang chủ</Link> <span>/</span>
        <Link to={`/san-pham?category=${product.category.slug}`}>{product.category.name}</Link> <span>/</span>
        <span>{product.name}</span>
      </nav>

      <div className="pdp">
        <div className="gallery">
          <div className="gallery-main">
            <ProductImage src={images[imageIndex]} alt={product.name} />
          </div>
          {images.length > 1 && (
            <div className="gallery-thumbs">
              {images.map((src, i) => (
                <button key={src} className={i === imageIndex ? 'active' : ''} onClick={() => setImageIndex(i)} aria-label={`Ảnh ${i + 1}`}>
                  <ProductImage src={src} alt="" />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="pdp-info">
          <span className="muted small upper">{product.category.name}</span>
          <h1 className="pdp-title">{product.name}</h1>
          {product.ratingCount > 0 && (
            <a href="#danh-gia" className="pdp-rating">
              <Stars value={product.ratingAverage} size={16} /> {product.ratingAverage.toFixed(1)} · {product.ratingCount} đánh giá
            </a>
          )}
          <Price price={product.price} compareAtPrice={product.compareAtPrice} size="lg" />

          <div className="option-group">
            <div className="option-label">Màu sắc: <strong>{color}</strong></div>
            <div className="swatch-picker">
              {product.colors.map((c) => {
                const available = product.variants.some((v) => v.color === c && v.stock > 0)
                return (
                  <button key={c} className={`swatch-btn ${color === c ? 'active' : ''} ${available ? '' : 'unavailable'}`}
                    onClick={() => { setColor(c); setQuantity(1) }} title={c} aria-label={c} aria-pressed={color === c}>
                    <span className="swatch swatch-lg" style={{ background: swatchColor(c) }} />
                  </button>
                )
              })}
            </div>
          </div>

          <div className="option-group">
            <div className="option-label">Kích cỡ: <strong>{size ?? 'Chưa chọn'}</strong></div>
            <div className="size-picker">
              {product.sizes.map((s) => {
                const v = variantFor(s, color)
                const out = !v || v.stock === 0
                return (
                  <button key={s} className={`size-btn ${size === s ? 'active' : ''}`} disabled={out}
                    onClick={() => { setSize(s); setQuantity(1) }} title={out ? 'Hết hàng' : undefined}>
                    {s}
                  </button>
                )
              })}
            </div>
            {selected && (
              <p className={`small ${selected.stock <= 3 ? 'text-warning' : 'muted'}`}>
                {selected.stock <= 3 ? `Chỉ còn ${selected.stock} sản phẩm` : 'Còn hàng'}
              </p>
            )}
            {color && product.sizes.every((s) => !(variantFor(s, color)?.stock > 0)) && (
              <p className="small text-danger">Màu này đã hết hàng</p>
            )}
          </div>

          <div className="pdp-actions">
            <QuantityStepper value={quantity} max={Math.max(1, maxQty)} onChange={setQuantity} />
            <button className="btn btn-primary btn-lg grow" onClick={() => addToCart(false)} disabled={selected && selected.stock === 0}>
              <Icon name="bag" size={18} /> Thêm vào giỏ
            </button>
          </div>
          <button className="btn btn-outline btn-lg btn-block" onClick={() => addToCart(true)}>Mua ngay</button>

          <ul className="pdp-perks">
            <li><Icon name="truck" size={18} /> Miễn phí vận chuyển cho đơn từ {formatPrice(cart.freeShippingThreshold)}</li>
            <li><Icon name="cash" size={18} /> Thanh toán khi nhận hàng</li>
          </ul>

          {product.description && (
            <div className="pdp-description">
              <h3>Mô tả sản phẩm</h3>
              <p>{product.description}</p>
            </div>
          )}
        </div>
      </div>

      <Reviews product={product} onReviewed={(p) => setProduct(p)} />

      {related.length > 0 && (
        <section className="section">
          <div className="section-head"><h2>Có thể bạn cũng thích</h2></div>
          <ProductGrid products={related} />
        </section>
      )}
    </div>
  )
}

function Reviews({ product, onReviewed }) {
  const { user } = useAuth()
  const location = useLocation()
  const toast = useToast()
  const [reviews, setReviews] = useState(null)
  const [eligibility, setEligibility] = useState(null)
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.reviews(product.slug).then(setReviews).catch(() => setReviews({ items: [], totalElements: 0 }))
    api.reviewEligibility(product.slug).then(setEligibility).catch(() => setEligibility(null))
  }, [product.slug, user])

  const submit = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await api.addReview(product.slug, rating, comment)
      toast.success('Cảm ơn bạn đã đánh giá!')
      setComment('')
      const [list, elig, fresh] = await Promise.all([
        api.reviews(product.slug), api.reviewEligibility(product.slug), api.product(product.slug),
      ])
      setReviews(list)
      setEligibility(elig)
      onReviewed(fresh)
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="section reviews" id="danh-gia">
      <div className="section-head"><h2>Đánh giá sản phẩm</h2></div>
      <div className="reviews-layout">
        <div className="review-summary">
          <div className="review-score">{product.ratingCount > 0 ? product.ratingAverage.toFixed(1) : '–'}</div>
          <Stars value={product.ratingAverage} size={18} />
          <p className="muted small">{product.ratingCount} đánh giá</p>

          {eligibility?.canReview && (
            <form className="review-form" onSubmit={submit}>
              <h4>Viết đánh giá của bạn</h4>
              <StarInput value={rating} onChange={setRating} />
              <textarea value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} rows={4}
                placeholder="Chia sẻ cảm nhận về chất liệu, kích cỡ, màu sắc..." />
              <ErrorBox error={error} />
              <button className="btn btn-primary btn-block" disabled={submitting}>
                {submitting ? 'Đang gửi...' : 'Gửi đánh giá'}
              </button>
            </form>
          )}
          {eligibility?.reason === 'LOGIN_REQUIRED' && (
            <p className="small muted">
              <Link to="/dang-nhap" state={{ from: location.pathname }} className="link">Đăng nhập</Link> để đánh giá sản phẩm bạn đã mua.
            </p>
          )}
          {eligibility?.reason === 'NOT_PURCHASED' && (
            <p className="small muted">Bạn có thể đánh giá sau khi đã nhận được sản phẩm này.</p>
          )}
          {eligibility?.reason === 'ALREADY_REVIEWED' && <p className="small muted">Bạn đã đánh giá sản phẩm này.</p>}
        </div>

        <div className="review-list">
          {reviews === null ? (
            <Spinner />
          ) : reviews.items.length === 0 ? (
            <p className="muted">Chưa có đánh giá nào. Hãy là người đầu tiên!</p>
          ) : (
            reviews.items.map((r) => (
              <article key={r.id} className="review">
                <div className="review-head">
                  <span className="avatar">{r.authorName.charAt(0).toUpperCase()}</span>
                  <div>
                    <strong>{r.authorName}</strong>
                    <div className="review-meta">
                      <Stars value={r.rating} size={12} /> <span className="muted small">{formatDate(r.createdAt)}</span>
                    </div>
                  </div>
                  <span className="badge badge-success review-verified"><Icon name="check" size={12} /> Đã mua hàng</span>
                </div>
                {r.comment && <p>{r.comment}</p>}
              </article>
            ))
          )}
        </div>
      </div>
    </section>
  )
}
