/**
 * JSON 工具（联调辅助）
 *
 * 作用：
 * - 把对象/数组格式化成“可读的 JSON 字符串”，用于页面展示与复制
 * - 在本项目里主要用于：把请求体/响应体渲染到 `<textarea>` 或 `<pre>` 中
 *
 * 注意：
 * - JSON.stringify 可能因为循环引用等原因抛错；这里做了兜底，保证 UI 不会直接崩溃
 */
export function prettyJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    // 极端情况下（例如循环引用），退化为 String()，至少能看到一个可展示的文本
    return String(value)
  }
}
