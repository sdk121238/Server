// 同时供浏览器和Node测试使用的接口导航工具模块。
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.SwaggerNavigation = factory();
})(typeof window !== "undefined" ? window : this, function () {
  "use strict";
  const methods = new Set(["get", "post", "put", "patch", "delete", "options", "head", "trace"]);
  // 从OpenAPI定义提取接口并按模块顺序排列。
  function buildOperations(spec) {
    const operations = [];
    const tagOrder = new Map((spec.tags || []).map((tag, index) => [tag.name, index]));
    for (const [path, pathItem] of Object.entries(spec.paths || {})) {
      for (const [method, operation] of Object.entries(pathItem)) {
        if (!methods.has(method) || !operation || typeof operation !== "object") continue;
        const tag = operation.tags && operation.tags[0] || "其他";
        operations.push({
          key: method.toUpperCase() + " " + path,
          method: method.toUpperCase(), path, tag,
          summary: operation.summary || path,
          operationId: operation.operationId,
          requiresAuth: (operation.security || spec.security || []).length > 0
        });
      }
    }
    return operations.sort((left, right) => (tagOrder.get(left.tag) ?? 999) - (tagOrder.get(right.tag) ?? 999));
  }
  // 按名称、路径、方法和模块执行多关键词筛选。
  function filterOperations(operations, query) {
    const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
    return operations.filter(operation => {
      const text = [operation.method, operation.path, operation.summary, operation.tag].join(" ").toLowerCase();
      return words.every(word => text.includes(word));
    });
  }
  // 生成可分享的选中接口链接片段。
  function operationHash(operation) {
    return "#/" + encodeURIComponent(operation.tag) + "/" + encodeURIComponent(operation.operationId);
  }
  // 解析链接并恢复对应接口，无效编码或未知接口返回null。
  function operationFromHash(operations, hash) {
    try {
      const parts = hash.replace(/^#\//, "").split("/").map(decodeURIComponent);
      if (parts.length !== 2) return null;
      return operations.find(operation => operation.tag === parts[0] && operation.operationId === parts[1]) || null;
    } catch (_) { return null; }
  }
  return { buildOperations, filterOperations, operationHash, operationFromHash };
});
