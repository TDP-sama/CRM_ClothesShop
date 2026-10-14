import { Link } from 'react-router-dom'
import { discountPercent, swatchColor } from '../lib/format.js'
import { Price, ProductImage, Stars } from './ui.jsx'

const NEW_DAYS = 14

export default function ProductCard({ product }) {
  const off = discountPercent(product.price, product.compareAtPrice)
  const isNew = product.createdAt && Date.now() - new Date(product.createdAt).getTime() < NEW_DAYS * 86400000
  return (
    <Link to={`/san-pham/${product.slug}`} className="product-card">
      <div className="product-card-media">
        <ProductImage src={product.imageUrl} alt={product.name} className="img-main" />
        {product.hoverImageUrl && <ProductImage src={product.hoverImageUrl} alt="" className="img-hover" />}
        <div className="product-card-badges">
          {off > 0 && <span className="badge badge-sale">-{off}%</span>}
          {isNew && !off && <span className="badge badge-new">Mới</span>}
          {!product.inStock && <span className="badge badge-muted">Hết hàng</span>}
        </div>
      </div>
      <div className="product-card-body">
        <div className="product-card-meta">
          <span className="muted small">{product.categoryName}</span>
          <span className="swatches">
            {product.colors.slice(0, 4).map((c) => (
              <span key={c} className="swatch swatch-xs" style={{ background: swatchColor(c) }} title={c} />
            ))}
          </span>
        </div>
        <h3 className="product-card-name">{product.name}</h3>
        <Price price={product.price} compareAtPrice={product.compareAtPrice} size="sm" />
        {product.ratingCount > 0 && <Stars value={product.ratingAverage} count={product.ratingCount} size={12} />}
      </div>
    </Link>
  )
}

export function ProductGrid({ products, loading, columns = 4 }) {
  if (loading) {
    return (
      <div className={`product-grid cols-${columns}`}>
        {Array.from({ length: columns * 2 }, (_, i) => (
          <div key={i} className="product-card skeleton-card">
            <div className="skeleton skeleton-media" />
            <div className="skeleton skeleton-line" />
            <div className="skeleton skeleton-line short" />
          </div>
        ))}
      </div>
    )
  }
  return (
    <div className={`product-grid cols-${columns}`}>
      {products.map((p) => (
        <ProductCard key={p.id} product={p} />
      ))}
    </div>
  )
}
