// Скелетоны загрузки: серые заглушки на месте контента, пока сервер отвечает.
// Появляются, только если ответ идет дольше 150 мс (быстрые страницы не мигают), и держатся не меньше 300 мс.
(function () {
    var SHOW_AFTER = 150;
    var MIN_SHOW = 300;

    function bar(w, cls) {
        return '<span class="sk ' + (cls || 'l') + '"' + (w ? ' style="width:' + w + '"' : '') + '></span>';
    }

    function tableRows(n) {
        var html = '';
        for (var i = 0; i < n; i++) {
            html += '<div class="sk-row">' + bar('70%') + '<div class="sk-stack">' + bar() + bar('60%') + '</div>' + bar('40%') + bar('', 'p')
                + bar('50%') + bar('70%') + bar('', 'b') + '</div>';
        }
        return html;
    }

    function templates(kind) {
        switch (kind) {
            case 'table':
                return '<div class="sk-table">' + tableRows(10) + '</div>';
            case 'board':
                var cols = '';
                [3, 2, 1, 2].forEach(function (n) {
                    cols += '<div class="sk-col">' + bar('50%');
                    for (var i = 0; i < n; i++) {
                        cols += '<div class="sk-card"><div class="sk-stack">' + bar() + bar('70%') + bar('', 'p') + '</div></div>';
                    }
                    cols += '</div>';
                });
                return '<div class="sk-board">' + cols + '</div>';
            case 'card':
                return '<div class="sk-stack sk-hero">' + bar('160px') + bar('240px', 't') + bar('50%') + '</div>'
                    + '<div class="sk-split"><div class="sk-stack">' + bar('120px', 't') + bar() + bar() + bar('70%') + bar('180px', 't') + bar() + bar('80%')
                    + '</div><div class="sk-stack">' + bar('140px', 't') + bar('80%') + bar('60%') + bar('70%') + '</div></div>';
            default:
                return '<div class="sk-stack">' + bar('220px', 't') + bar('40%') + bar() + bar() + bar('75%') + bar() + bar('60%') + '</div>';
        }
    }

    function kindFor(path) {
        if (/^\/(catalog|artifacts|firmware|cases|search)(\/|$)/.test(path)) { return 'table'; }
        if (/^\/tasks(\/|$)/.test(path)) { return 'board'; }
        if (/^\/skus\/\d+/.test(path)) { return 'card'; }
        return 'page';
    }

    // ----- Переход по ссылке: на время ожидания подменяем содержимое страницы -----
    var pageTimer = null;
    var pageSkeleton = null;

    function clearPage() {
        clearTimeout(pageTimer);
        pageTimer = null;
        if (pageSkeleton) {
            pageSkeleton.remove();
            pageSkeleton = null;
        }
        var content = document.querySelector('.content');
        if (content) { content.classList.remove('sk-hidden'); }
    }

    document.addEventListener('click', function (e) {
        var a = e.target.closest && e.target.closest('a[href]');
        if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) { return; }
        if (a.target && a.target !== '_self' || a.hasAttribute('download') || a.hasAttribute('hx-get') || a.hasAttribute('hx-post')) { return; }
        var url;
        try { url = new URL(a.href, location.href); } catch (err) { return; }
        if (url.origin !== location.origin || (url.pathname === location.pathname && url.search === location.search)) { return; }
        // скачивания и файлы не заменяют страницу: скелетон показываем только для обычных страниц приложения
        if (/\.(pdf|zip|xlsx?|docx?|pptx?|png|jpe?g|svg|webp|mp4|md|txt)$/i.test(url.pathname) || /\/(download|files|export|preview)(\/|$)/.test(url.pathname)
            || url.searchParams.has('inline') || /^\/(logout|actuator|dev\/login-as)/.test(url.pathname)) { return; }
        if (!/^\/(catalog|artifacts|firmware|cases|tasks|dashboard|sku|skus|directories|settings|feedback|search|questions)?(\/|$)/.test(url.pathname)) { return; }
        clearTimeout(pageTimer);
        pageTimer = setTimeout(function () {
            var content = document.querySelector('.content');
            if (!content || pageSkeleton) { return; }
            pageSkeleton = document.createElement('div');
            pageSkeleton.className = 'sk-page sk-in';
            pageSkeleton.setAttribute('aria-hidden', 'true');
            pageSkeleton.innerHTML = '<div class="sk-stack sk-head">' + bar('220px', 't') + bar('160px') + '</div>' + templates(kindFor(url.pathname));
            content.classList.add('sk-hidden');
            content.parentNode.insertBefore(pageSkeleton, content.nextSibling);
            // страховка: если страница так и не сменилась (например, ответ оказался файлом), заглушка не должна остаться
            setTimeout(clearPage, 8000);
        }, SHOW_AFTER);
    });
    window.addEventListener('pageshow', clearPage);
    window.addEventListener('focus', function () { if (pageSkeleton) { setTimeout(clearPage, 600); } });
    document.addEventListener('visibilitychange', function () { if (!document.hidden && pageSkeleton) { setTimeout(clearPage, 600); } });
    window.addEventListener('pagehide', function () { clearTimeout(pageTimer); });

    // ----- HTMX: фильтры и страницы списков меняют только таблицу или доску -----
    var overlays = new WeakMap();

    document.body.addEventListener('htmx:beforeRequest', function (e) {
        var target = e.detail.target;
        if (!target || !(target.id === 'results' || target.id === 'board')) { return; }
        var state = {timer: null, el: null, shownAt: 0};
        overlays.set(target, state);
        state.timer = setTimeout(function () {
            var r = target.getBoundingClientRect();
            var el = document.createElement('div');
            el.className = 'sk-overlay sk-in';
            el.setAttribute('aria-hidden', 'true');
            el.style.cssText = 'position:absolute;left:' + (r.left + window.scrollX) + 'px;top:' + (r.top + window.scrollY) + 'px;width:' + r.width
                + 'px;min-height:' + Math.min(Math.max(r.height, 240), 640) + 'px';
            el.innerHTML = templates(target.id === 'board' ? 'board' : 'table');
            document.body.appendChild(el);
            state.el = el;
            state.shownAt = Date.now();
        }, SHOW_AFTER);
    });

    function finish(e) {
        var target = e.detail.target || (e.detail.elt && e.detail.elt);
        var state = target && overlays.get(target);
        if (!state) { return; }
        clearTimeout(state.timer);
        overlays.delete(target);
        if (!state.el) { return; }
        var wait = Math.max(0, MIN_SHOW - (Date.now() - state.shownAt));
        setTimeout(function () { state.el.remove(); }, wait);
    }
    document.body.addEventListener('htmx:afterRequest', finish);
    document.body.addEventListener('htmx:sendError', finish);
})();
