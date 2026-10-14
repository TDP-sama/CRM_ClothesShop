import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'

const STORAGE_KEY = 'moda-cart'
const MAX_PER_ITEM = 20

const CartContext = createContext(null)

function loadCart() {
  try {
    const items = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    return Array.isArray(items) ? items : []
  } catch {
    return []
  }
}

/**
 * The cart lives in the browser (no login needed to shop). Prices here are for display only;
 * the server recalculates everything from the database at checkout.
 */
export function CartProvider({ children }) {
  const [items, setItems] = useState(loadCart)
  const [config, setConfig] = useState({ shippingFee: 30000, freeShippingThreshold: 500000 })
  const [drawerOpen, setDrawerOpen] = useState(false)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
    } catch {
      /* storage full or blocked: the cart still works for this visit */
    }
  }, [items])

  useEffect(() => {
    api.config().then(setConfig).catch(() => {})
  }, [])

  const add = useCallback((item, quantity = 1) => {
    setItems((prev) => {
      const limit = Math.min(MAX_PER_ITEM, item.stock)
      const existing = prev.find((i) => i.variantId === item.variantId)
      if (existing) {
        return prev.map((i) =>
          i.variantId === item.variantId ? { ...i, stock: item.stock, quantity: Math.min(limit, i.quantity + quantity) } : i,
        )
      }
      return [...prev, { ...item, quantity: Math.min(limit, quantity) }]
    })
  }, [])

  const setQuantity = useCallback((variantId, quantity) => {
    setItems((prev) =>
      prev.map((i) =>
        i.variantId === variantId ? { ...i, quantity: Math.max(1, Math.min(quantity, MAX_PER_ITEM, i.stock)) } : i,
      ),
    )
  }, [])

  const remove = useCallback((variantId) => setItems((prev) => prev.filter((i) => i.variantId !== variantId)), [])
  const clear = useCallback(() => setItems([]), [])

  const value = useMemo(() => {
    const count = items.reduce((sum, i) => sum + i.quantity, 0)
    const subtotal = items.reduce((sum, i) => sum + i.price * i.quantity, 0)
    const shippingFee = items.length === 0 || subtotal >= config.freeShippingThreshold ? 0 : config.shippingFee
    return {
      items,
      count,
      subtotal,
      shippingFee,
      total: subtotal + shippingFee,
      freeShippingThreshold: config.freeShippingThreshold,
      add,
      setQuantity,
      remove,
      clear,
      drawerOpen,
      openDrawer: () => setDrawerOpen(true),
      closeDrawer: () => setDrawerOpen(false),
    }
  }, [items, config, add, setQuantity, remove, clear, drawerOpen])

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export const useCart = () => useContext(CartContext)
