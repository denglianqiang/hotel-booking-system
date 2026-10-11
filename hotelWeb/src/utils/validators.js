/**
 * 表单校验规则的公共部分
 *
 * 手机号 / 身份证号的正则在多个表单里重复出现，统一放在这里，
 * 避免某处调整校验后其它页面漏改。
 */

// 手机号：11 位，1 开头，第二位 3-9
export const PHONE_PATTERN = /^1[3-9]\d{9}$/

// 身份证号：18 位，末位可为 X；同时允许空串（选填）
export const ID_CARD_PATTERN = /^$|^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/

/**
 * 手机号校验规则
 *
 * @param {Object}  [options]
 * @param {boolean} [options.required]        是否必填
 * @param {string}  [options.requiredMessage] 必填时的提示文案，默认“请输入电话”
 * @param {string}  [options.message]         格式错误时的提示文案，默认“请输入正确的手机号”
 * @returns {Array} Element Plus 规则数组
 */
export function phoneRules({
  required = false,
  requiredMessage = '请输入电话',
  message = '请输入正确的手机号',
} = {}) {
  const rules = []
  if (required) {
    rules.push({ required: true, message: requiredMessage, trigger: 'blur' })
  }
  rules.push({ pattern: PHONE_PATTERN, message, trigger: 'blur' })
  return rules
}

/**
 * 身份证号校验规则（选填；填写则必须是 18 位，与后端 CustomerDTO 的校验保持一致）
 *
 * @returns {Array} Element Plus 规则数组
 */
export function idCardRules() {
  return [
    {
      pattern: ID_CARD_PATTERN,
      message: '身份证号格式不正确（应为18位）',
      trigger: 'blur',
    },
  ]
}
