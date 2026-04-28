/**
 * 错误文案工具
 *
 * 作用：
 * - 把 HTTP 状态码（400/404/409/500/...）映射成“用户可理解”的提示
 * - 让页面层只需要展示 message，不用每个页面重复写 switch/case
 *
 * 调用链：
 * - api/http.ts 的响应拦截器在捕获到 axios error 后，会调用这里把状态码转成 message
 * - 页面 catch(e) 拿到的是 NormalizedHttpError，直接展示 err.message（由此生成）
 */
export function friendlyHttpMessage(status?: number): string {
  switch (status) {
    case 400:
      return '参数校验失败或 JSON 格式错误，请检查必填字段与类型。'
    case 404:
      return '合同不存在，请先在“合同导入”页完成导入。'
    case 409:
      return '合同 ID 已存在，请更换 id 后重试。'
    case 500:
      return '服务异常或模型调用失败，请稍后重试。'
    default:
      return status ? `请求失败（HTTP ${status}）` : '网络异常，请检查服务是否已启动。'
  }
}
