export const STORE_NAME = 'MODA'

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
export const formatPrice = (value) => money.format(value ?? 0)

const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' })
const date = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' })
export const formatDateTime = (iso) => (iso ? dateTime.format(new Date(iso)) : '')
export const formatDate = (iso) => (iso ? date.format(new Date(iso)) : '')

export const discountPercent = (price, compareAtPrice) =>
  compareAtPrice && compareAtPrice > price ? Math.round((1 - price / compareAtPrice) * 100) : 0

export const ORDER_STATUS = {
  PENDING: { label: 'Chờ xác nhận', tone: 'warning' },
  CONFIRMED: { label: 'Đã xác nhận', tone: 'info' },
  SHIPPING: { label: 'Đang giao', tone: 'info' },
  DELIVERED: { label: 'Đã giao', tone: 'success' },
  CANCELLED: { label: 'Đã hủy', tone: 'danger' },
}
export const ORDER_FLOW = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED']

export const ROLES = {
  CUSTOMER: { label: 'Khách hàng', tone: 'neutral' },
  STAFF: { label: 'Nhân viên', tone: 'info' },
  ADMIN: { label: 'Quản trị viên', tone: 'success' },
}

/** Admin-area sections that can be granted to staff, in sidebar order. */
export const ADMIN_SECTIONS = [
  { id: 'DASHBOARD', label: 'Tổng quan', path: '/quan-tri', icon: 'dashboard' },
  { id: 'PRODUCTS', label: 'Sản phẩm', path: '/quan-tri/san-pham', icon: 'tag' },
  { id: 'CATEGORIES', label: 'Danh mục', path: '/quan-tri/danh-muc', icon: 'folder' },
  { id: 'ORDERS', label: 'Đơn hàng', path: '/quan-tri/don-hang', icon: 'package' },
  { id: 'INVENTORY', label: 'Kho hàng', path: '/quan-tri/kho', icon: 'warehouse' },
  { id: 'REPORTS', label: 'Báo cáo', path: '/quan-tri/bao-cao', icon: 'chart' },
]

export const STOCK_STATUS = {
  IN_STOCK: { label: 'Còn hàng', tone: 'success' },
  LOW: { label: 'Sắp hết', tone: 'warning' },
  OUT: { label: 'Hết hàng', tone: 'danger' },
}

export const MOVEMENT_TYPES = {
  INITIAL: { label: 'Tồn ban đầu', tone: 'neutral' },
  RECEIPT: { label: 'Nhập kho', tone: 'success' },
  SALE: { label: 'Xuất bán', tone: 'info' },
  RETURN: { label: 'Hoàn kho', tone: 'warning' },
  ADJUSTMENT: { label: 'Điều chỉnh', tone: 'danger' },
}

export const ADJUSTMENT_REASONS = {
  COUNT: 'Kiểm kê',
  DAMAGED: 'Hàng lỗi / hỏng',
  LOST: 'Thất lạc',
  OTHER: 'Khác',
}

/** Swatch colors for the Vietnamese color names used in the catalog; unknown names fall back to gray. */
const SWATCHES = {
  'trắng': '#ffffff',
  'đen': '#151515',
  'xám': '#9ca3af',
  'kem': '#efe6d2',
  'be': '#e2d3b8',
  'xanh denim': '#4a6fa5',
  'xanh nhạt': '#a9c8e8',
  'xanh đậm': '#1f3a5f',
  'xanh navy': '#1b2a4a',
  'xanh lá': '#3f7d4e',
  'đỏ': '#c8102e',
  'đỏ đô': '#7b1e2b',
  'hồng': '#f2b6c4',
  'vàng': '#f2c94c',
  'tím': '#6b3fa0',
  'cam': '#f08a24',
  'cam đất': '#b8613a',
  'nâu': '#7a5230',
}
export const swatchColor = (name) => SWATCHES[name?.toLowerCase()] ?? '#c7c7c7'
