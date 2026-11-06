// 启动调试台：加载接口定义，绑定导航事件并挂载Swagger测试表单。
window.addEventListener("load", async function () {
  "use strict";
  const navigation = window.SwaggerNavigation;
  const search = document.getElementById("api-search");
  const list = document.getElementById("api-list");
  const root = document.getElementById("swagger-ui");
  const workspace = document.getElementById("test-workspace");
  const message = document.getElementById("workspace-message");
  const menuButton = document.getElementById("nav-toggle");
  let operations = [], selected = null, openedKey = null, updateScheduled = false;

  // 打开或关闭窄屏导航，并同步无障碍展开状态。
  function menu(open) {
    document.body.classList.toggle("nav-open", open);
    menuButton.setAttribute("aria-expanded", String(open));
  }
  menuButton.addEventListener("click", () => menu(!document.body.classList.contains("nav-open")));
  document.getElementById("nav-backdrop").addEventListener("click", () => menu(false));
  document.addEventListener("keydown", event => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      if (window.matchMedia("(max-width: 840px)").matches) menu(true);
      search.focus();
    }
    if (event.key === "Escape") menu(false);
  });

  // 根据搜索结果重建模块导航，保留当前选中标记。
  function renderNavigation() {
    const visible = navigation.filterOperations(operations, search.value);
    document.getElementById("nav-summary").textContent = search.value.trim()
      ? "找到 " + visible.length + " 个接口" : operations.length + " 个接口 · 按模块分组";
    const fragment = document.createDocumentFragment();
    const groups = new Map();
    for (const operation of visible) {
      if (!groups.has(operation.tag)) groups.set(operation.tag, []);
      groups.get(operation.tag).push(operation);
    }
    for (const [tag, group] of groups) {
      const section = document.createElement("details");
      section.className = "api-group";
      section.open = true;
      const heading = document.createElement("summary");
      const label = document.createElement("span");
      label.textContent = tag;
      const count = document.createElement("span");
      count.className = "group-count";
      count.textContent = String(group.length);
      heading.append(label, count);
      section.append(heading);
      for (const operation of group) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "api-item";
        button.dataset.operationKey = operation.key;
        button.title = operation.method + " " + operation.path + " · " + operation.summary;
        button.setAttribute("aria-current", selected && selected.key === operation.key ? "page" : "false");
        const method = document.createElement("span");
        method.className = "method-badge method-" + operation.method.toLowerCase();
        method.textContent = operation.method;
        const copy = document.createElement("span");
        copy.className = "api-item-copy";
        const title = document.createElement("span");
        title.className = "api-item-title";
        title.textContent = operation.summary;
        const path = document.createElement("span");
        path.className = "api-item-path";
        path.textContent = operation.path;
        copy.append(title, path);
        button.append(method, copy);
        button.addEventListener("click", () => selectOperation(operation, true));
        section.append(button);
      }
      fragment.append(section);
    }
    if (!visible.length) {
      const empty = document.createElement("p");
      empty.className = "navigation-empty";
      empty.textContent = "未找到匹配接口，请尝试其他名称、路径或方法。";
      fragment.append(empty);
    }
    list.replaceChildren(fragment);
  }

  // Leave Swagger forms mounted so switching preserves request inputs and responses.
  // 只显示选中的Swagger操作，保留已挂载表单以保存输入和响应。
  function revealSelection() {
    updateScheduled = false;
    if (!selected) return;
    const block = document.getElementById("operations-" + selected.tag + "-" + selected.operationId);
    root.querySelectorAll(".opblock").forEach(element => element.setAttribute("data-nav-selected", String(element === block)));
    root.querySelectorAll(".opblock-tag-section").forEach(section => section.setAttribute("data-nav-selected", String(!!block && section.contains(block))));
    if (!block) return;
    message.hidden = true;
    workspace.setAttribute("aria-busy", "false");
    if (openedKey !== selected.key) {
      const toggle = block.querySelector(".opblock-summary-control");
      if (toggle) {
        openedKey = selected.key;
        if (!block.classList.contains("is-open")) toggle.click();
      }
    }
  }
  // 合并DOM变更，在下一帧更新选中接口的显示状态。
  function scheduleSelection() {
    if (updateScheduled) return;
    updateScheduled = true;
    window.requestAnimationFrame(revealSelection);
  }
  // 同步导航、标题、授权提示和链接，并将测试区滚动到顶部。
  function selectOperation(operation, updateHash) {
    selected = operation;
    openedKey = null;
    document.getElementById("active-group").textContent = operation.tag;
    document.getElementById("active-title").textContent = operation.summary;
    const access = document.getElementById("active-access");
    access.hidden = false;
    access.textContent = operation.requiresAuth ? "需要登录" : "无需登录";
    access.classList.toggle("is-public", !operation.requiresAuth);
    list.querySelectorAll(".api-item").forEach(button => button.setAttribute("aria-current", button.dataset.operationKey === operation.key ? "page" : "false"));
    if (updateHash && window.location.hash !== navigation.operationHash(operation)) window.location.hash = navigation.operationHash(operation);
    workspace.scrollTop = 0;
    menu(false);
    scheduleSelection();
  }
  search.addEventListener("input", renderNavigation);
  window.addEventListener("hashchange", () => {
    const operation = navigation.operationFromHash(operations, window.location.hash);
    if (operation && (!selected || selected.key !== operation.key)) selectOperation(operation, false);
  });

  try {
    const response = await fetch("../v3/api-docs", { headers: { Accept: "application/json" }, cache: "no-store" });
    if (!response.ok) throw new Error("接口定义返回 HTTP " + response.status);
    const spec = await response.json();
    operations = navigation.buildOperations(spec);
    if (!operations.length) throw new Error("当前没有可用接口");
    document.getElementById("api-count").textContent = String(operations.length);
    document.getElementById("api-version").textContent = "v" + (spec.info.version || "1.0.0");
    renderNavigation();
    selectOperation(navigation.operationFromHash(operations, window.location.hash) || operations[0], false);
    new MutationObserver(scheduleSelection).observe(root, { childList: true, subtree: true });
    window.ui = SwaggerUIBundle({
      spec, dom_id: "#swagger-ui", deepLinking: false, filter: false,
      docExpansion: "list", tryItOutEnabled: true, displayRequestDuration: true,
      defaultModelsExpandDepth: -1, persistAuthorization: false, validatorUrl: null,
      queryConfigEnabled: false, presets: [SwaggerUIBundle.presets.apis],
      layout: "BaseLayout", onComplete: scheduleSelection
    });
  } catch (error) {
    workspace.setAttribute("aria-busy", "false");
    document.getElementById("active-title").textContent = "接口加载失败";
    document.getElementById("nav-summary").textContent = "暂时无法加载接口";
    document.getElementById("api-count").textContent = "—";
    message.textContent = (error.message || "无法加载接口定义") + "。请确认后端已启动，然后刷新页面重试。";
    message.classList.add("is-error");
  }
});
