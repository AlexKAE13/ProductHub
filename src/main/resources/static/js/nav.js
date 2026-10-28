// Быстрый отклик навигации: полоса прогресса сразу при клике (до ответа сервера) и подгрузка страниц списков при наведении.
(function () {
    var bar = document.createElement('div');
    bar.id = 'nav-progress';
    bar.setAttribute('role', 'progressbar');
    bar.setAttribute('aria-hidden', 'true');
    document.documentElement.appendChild(bar);
    var timer = null;

    function start() {
        clearTimeout(timer);
        bar.className = 'run';
        bar.style.width = '0';
        // два кадра: сначала ширина 0, затем плавный рост до 80%, чтобы переход анимировался
        requestAnimationFrame(function () {
            requestAnimationFrame(function () { bar.style.width = '80%'; });
        });
    }

    function finish() {
        bar.style.width = '100%';
        timer = setTimeout(function () { bar.className = ''; bar.style.width = '0'; }, 200);
    }

    // Переходы по обычным ссылкам внутри приложения (скачивания и вкладки не считаем)
    document.addEventListener('click', function (e) {
        var a = e.target.closest && e.target.closest('a[href]');
        if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) { return; }
        if ((a.target && a.target !== '_self') || a.hasAttribute('download') || a.hasAttribute('hx-get') || a.hasAttribute('hx-post')) { return; }
        var url;
        try { url = new URL(a.href, location.href); } catch (err) { return; }
        if (url.origin !== location.origin || (url.pathname === location.pathname && url.search === location.search)) { return; }
        if (/\.[a-z0-9]{2,4}$/i.test(url.pathname) || /\/(download|files|export|preview)(\/|$)/.test(url.pathname) || /^\/(logout|actuator)/.test(url.pathname)) { return; }
        start();
    }, true);

    // Запросы HTMX (фильтры, блоки карточки), кроме подсказок поиска: они частые и мигали бы
    document.addEventListener('htmx:beforeRequest', function (e) {
        var path = (e.detail && e.detail.pathInfo && e.detail.pathInfo.requestPath) || '';
        if (path.indexOf('/search/suggest') === 0) { return; }
        start();
    });
    ['htmx:afterOnLoad', 'htmx:responseError', 'htmx:sendError'].forEach(function (name) {
        document.addEventListener(name, finish);
    });

    // Возврат кнопкой "Назад" из кэша страниц браузера: убрать застывшую полосу
    window.addEventListener('pageshow', function (e) { if (e.persisted) { bar.className = ''; bar.style.width = '0'; } });

    // Подгрузка страниц списков при наведении (Chrome, Edge). Только страницы без побочных эффектов:
    // карточки и поиск исключены, чтобы не искажать статистику просмотров и запросов.
    if (window.HTMLScriptElement && HTMLScriptElement.supports && HTMLScriptElement.supports('speculationrules')) {
        var rules = document.createElement('script');
        rules.type = 'speculationrules';
        rules.textContent = JSON.stringify({
            prefetch: [{
                where: { or: ['/catalog', '/artifacts', '/firmware', '/cases', '/tasks', '/dashboard'].map(function (p) { return { href_matches: p }; }) },
                eagerness: 'moderate'
            }]
        });
        document.head.appendChild(rules);
    }
})();
