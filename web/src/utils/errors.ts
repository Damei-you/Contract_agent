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
