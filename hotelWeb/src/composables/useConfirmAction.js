import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 二次确认 + 执行 + 成功提示 + 刷新列表 的统一封装
 *
 * 集中原来散落在各列表页里的 confirm/action/success/reload 模板代码，
 * 并提供“同一时刻只允许一个操作在途”的行级状态，避免按钮被重复点击。
 *
 * 业务异常不需要在这里提示：请求拦截器已经统一弹出错误信息。
 */
export function useConfirmAction() {
  // 正在执行的行标识（行 id 或任意 key），未执行时为 null
  const acting = ref(null)

  // 该行是否正在执行操作
  function isActing(key) {
    return acting.value !== null && acting.value === key
  }

  /**
   * 执行一次操作
   *
   * @param {Object}   options
   * @param {*}        [options.row]      本次操作所属的行标识，作为 acting 的值
   * @param {string}   [options.message]  确认文案；不传则不弹确认框（如只读的“操作记录”）
   * @param {string}   [options.title]    确认框标题，默认“提示”
   * @param {Object}   [options.prompt]   传入则改用可输入文案的输入框（如取消原因），
   *                                      字段：inputType / inputPlaceholder / confirmButtonText / cancelButtonText
   * @param {Function} options.action     必填，返回 Promise 的业务请求
   * @param {string|Function} [options.success] 成功提示；传函数时入参为接口返回体，便于按返回值拼提示
   * @param {Function} [options.onDone]   成功后的收尾动作（通常是刷新列表），会 await
   * @returns {Promise<boolean>} 操作是否成功完成（用户取消或接口失败返回 false）
   */
  async function run(options) {
    const {
      row = null,
      message,
      title = '提示',
      prompt = null,
      action,
      success,
      onDone,
    } = options

    // 已有操作在途时不重复执行（用户快速连点、或点了另一行的按钮）
    if (acting.value !== null) return false

    // 先占位再弹窗：把“弹窗等待确认”也纳入在途状态，避免弹窗期间重复触发
    acting.value = row
    let result
    try {
      let input
      // message 缺省表示只读操作，不需要二次确认
      if (message) {
        const boxOptions = { type: 'warning', ...(prompt || {}) }
        if (prompt) {
          // 输入框确认（取消/退款原因等）：拿不到输入说明用户取消，直接结束
          const { value } = await ElMessageBox.prompt(message, title, boxOptions)
          input = value
        } else {
          await ElMessageBox.confirm(message, title, boxOptions)
        }
      }

      result = await action(input)
      ElMessage.success(typeof success === 'function' ? success(result) : success || '操作成功')
    } catch (e) {
      // 这里同时覆盖“用户取消”和“接口异常”，两者都不向外抛：
      // 接口错误的提示已由请求拦截器统一完成
      return false
    } finally {
      acting.value = null
    }

    await onDone?.()
    return true
  }

  return { acting, isActing, run }
}
