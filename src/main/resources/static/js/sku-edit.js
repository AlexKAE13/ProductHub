// Карточка SKU: режим правки включается кнопкой "Править" в шапке. Пока он выключен, кнопки правки и публикации скрыты.
(function () {
    var main = document.querySelector('.content.card');
    var toggle = document.getElementById('editToggle');
    if (!main || !toggle) {
        return;
    }
    function render(on) {
        main.classList.toggle('editing', on);
        toggle.textContent = on ? toggle.getAttribute('data-l-done') : toggle.getAttribute('data-l-edit');
        toggle.setAttribute('aria-pressed', on ? 'true' : 'false');
    }

    // Открытые формы правки блоков (описание, характеристики и др.) и их кнопки "Отмена"
    function openForms() {
        return [].slice.call(main.querySelectorAll('form[hx-post*="/edit/"]'));
    }
    main.addEventListener('input', function (e) {
        var form = e.target.closest && e.target.closest('form[hx-post*="/edit/"]');
        if (form) { form.dataset.dirty = '1'; }
    });

    toggle.addEventListener('click', function () {
        if (main.classList.contains('editing')) {
            // "Готово": закрываем все открытые формы, блоки возвращаются к обычному виду
            var forms = openForms();
            var dirty = forms.some(function (f) { return f.dataset.dirty === '1'; });
            if (dirty && !window.confirm(toggle.getAttribute('data-l-discard'))) {
                return;
            }
            forms.forEach(function (f) {
                var cancel = f.querySelector('button[hx-get*="/block/"]');
                if (cancel) { cancel.click(); }
            });
            render(false);
        } else {
            render(true);
        }
    });
    // Режим живет только на открытой странице: сохранение блока (замена через HTMX) его не сбрасывает,
    // а любой переход или обновление страницы возвращает обычный вид без кнопок правки.
    render(false);
    // возврат кнопкой "назад" показывает страницу из памяти браузера: режим правки при этом тоже выключаем
    window.addEventListener('pageshow', function (e) { if (e.persisted) { render(false); } });
})();

// Сворачивание блоков "Описание" и "Характеристики": состояние помнится в браузере и переживает сохранение блока
(function () {
    var KEY = 'hub-collapsed:';
    function stored(id) {
        try { return localStorage.getItem(KEY + id) === '1'; } catch (e) { return false; }
    }
    function store(id, on) {
        try { if (on) { localStorage.setItem(KEY + id, '1'); } else { localStorage.removeItem(KEY + id); } } catch (e) { /* без хранилища не запоминаем */ }
    }
    function paint(section, on) {
        section.classList.toggle('collapsed', on);
        var btn = section.querySelector('.block-toggle');
        if (btn) {
            btn.setAttribute('aria-expanded', on ? 'false' : 'true');
            var label = btn.getAttribute(on ? 'data-l-expand' : 'data-l-collapse');
            if (label) { btn.title = label; btn.setAttribute('aria-label', label); }
        }
    }
    function apply(instant) {
        document.querySelectorAll('[data-collapsible]').forEach(function (s) {
            var on = stored(s.getAttribute('data-collapsible'));
            if (instant) {
                // без анимации при загрузке страницы и после замены блока
                var inner = s.querySelector('.collapsible');
                if (inner) { inner.style.transition = 'none'; }
                paint(s, on);
                if (inner) { void inner.offsetHeight; inner.style.transition = ''; }
            } else {
                paint(s, on);
            }
        });
    }
    document.addEventListener('click', function (e) {
        var head = e.target.closest && e.target.closest('.block-head');
        if (!head) { return; }
        var section = head.closest('[data-collapsible]');
        if (!section) { return; }
        var onToggle = e.target.closest('.block-toggle');
        var onTitle = e.target.closest('.block-title');
        if (!onToggle && !onTitle) { return; }
        var id = section.getAttribute('data-collapsible');
        var on = !section.classList.contains('collapsed');
        store(id, on);
        paint(section, on);
    });
    // Открытие формы правки показывает блок развернутым
    document.body.addEventListener('htmx:beforeRequest', function (e) {
        var path = (e.detail.requestConfig && e.detail.requestConfig.path) || '';
        var m = /\/edit\/(text|specs)/.exec(path);
        if (m) { store(m[1], false); }
    });
    document.body.addEventListener('htmx:afterSwap', function () { apply(true); });
    apply(true);
})();
