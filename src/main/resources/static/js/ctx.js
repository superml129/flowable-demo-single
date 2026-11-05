/*
 * 上下文路径适配脚本（防御性预留）
 * ------------------------------------------------------------------
 * 当前部署形态是「根路径」：nginx 在 8080 对外，应用没有 context-path，
 * 页面里的 /login/toPage、/css/xxx.css 等地址与开发环境完全一致。
 * 此时 <html data-ctx> 渲染为 "/"，本脚本的前缀为空串，所有逻辑都是空操作。
 *
 * 保留它的原因：如果将来需要把应用挂到某个子路径（例如统一网关前缀），
 * 只需给应用加 context-path，页面里的绝对路径才不会绕过前缀——
 * 那时无需再改任何页面 JS。
 *
 * 机制：
 *   1. 读取 <html data-ctx="..."> 上由 Thymeleaf 的 @{/} 渲染出的上下文路径；
 *   2. 对 fetch / XMLHttpRequest / EventSource 的 URL 自动补上该前缀。
 */
(function () {
    'use strict';

    // data-ctx 由 @{/} 渲染：根路径部署时为 "/"，子路径部署时形如 "/apps/flowable/"
    var raw = (document.documentElement && document.documentElement.getAttribute('data-ctx')) || '/';
    var base = raw.replace(/\/+$/, ''); // 根路径为 ""，子路径为 "/apps/flowable"

    window.__CTX__ = base;

    /**
     * 给根路径开头的 URL 补上下文前缀；相对路径、绝对 URL、已带前缀的原样返回。
     */
    function ctxUrl(url) {
        if (url === null || url === undefined) return url;
        if (typeof url !== 'string' || url === '') return url;
        if (url.charAt(0) !== '/') return url;        // 相对路径 或 http(s)://...
        if (url.indexOf('//') === 0) return url;      // 协议相对地址
        if (!base) return url;                        // 开发环境，无需处理
        if (url === base || url.indexOf(base + '/') === 0) return url; // 已带前缀，避免重复
        return base + url;
    }

    window.ctxUrl = ctxUrl;

    // fetch
    if (typeof window.fetch === 'function') {
        var nativeFetch = window.fetch;
        window.fetch = function (input, init) {
            try {
                if (typeof input === 'string') {
                    input = ctxUrl(input);
                } else if (input && typeof input === 'object' && input.url) {
                    // Request 对象：URL 不可变，重建一个（兼容性处理）
                    if (typeof Request === 'function' && ctxUrl(input.url) !== input.url) {
                        input = new Request(ctxUrl(input.url), input);
                    }
                }
            } catch (e) {
                /* 转换失败时用原始入参，避免阻断业务 */
            }
            return nativeFetch.call(this, input, init);
        };
    }

    // XMLHttpRequest
    if (window.XMLHttpRequest && XMLHttpRequest.prototype && XMLHttpRequest.prototype.open) {
        var nativeOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function (method, url) {
            try {
                if (typeof url === 'string') {
                    arguments[1] = ctxUrl(url);
                }
            } catch (e) { /* ignore */ }
            return nativeOpen.apply(this, arguments);
        };
    }

    // EventSource（业务消息 SSE：/flowMsg/connect）
    if (typeof window.EventSource === 'function') {
        var NativeES = window.EventSource;
        var PatchedES = function (url, config) {
            return new NativeES(ctxUrl(url), config);
        };
        PatchedES.prototype = NativeES.prototype;
        PatchedES.CONNECTING = NativeES.CONNECTING;
        PatchedES.OPEN = NativeES.OPEN;
        PatchedES.CLOSED = NativeES.CLOSED;
        window.EventSource = PatchedES;
    }
})();
