/**
 * 金额展示格式化
 *
 * 表格 / 卡片里的“¥ + 金额”原来在各模板里手写，集中到这里统一口径，
 * 保证同一笔金额在不同页面渲染出的文本完全一致。
 */

/**
 * 金额格式化：168 → '¥168'，'168.5' → '¥168.5'
 *
 * @param {*} value      金额
 * @param {string} [empty=''] 空值（null / undefined / 空串）时的占位文案
 * @returns {string}
 */
export function formatMoney(value, empty = '') {
  if (value === null || value === undefined || value === '') return empty
  return `¥${value}`
}
