// Современное оформление: мгновенный предпросмотр в настройках и автоконтраст обоев.
(function () {
    var root = document.documentElement;

    // ----- Яркость обоев: считаем по уменьшенной копии, чтобы текст вне стеклянных панелей всегда читался -----
    var luma = 0.5;
    function resolvedTheme() {
        return root.dataset.theme === 'dark' ? 'dark' : 'light';
    }
    function applyTint() {
        var dim = parseFloat(getComputedStyle(root).getPropertyValue('--dim')) || 0;
        var th = resolvedTheme();
        var auto = th === 'dark' ? Math.max(0, Math.min(0.78, (luma - 0.2) * 1.25)) : Math.max(0, Math.min(0.72, (0.62 - luma) * 1.2));
        var eff = root.dataset.bgtype === 'photo' ? Math.max(auto, dim) : 0;
        root.style.setProperty('--tint', (th === 'dark' ? 'rgba(0,0,0,' : 'rgba(255,255,255,') + eff.toFixed(2) + ')');
    }
    function measure() {
        var m = /url\(["']?([^"')]+)["']?\)/.exec(getComputedStyle(root).getPropertyValue('--wall'));
        if (!m) { applyTint(); return; }
        var img = new Image();
        img.onload = function () {
            try {
                var c = document.createElement('canvas');
                c.width = c.height = 24;
                var g = c.getContext('2d');
                g.drawImage(img, 0, 0, 24, 24);
                var d = g.getImageData(0, 0, 24, 24).data;
                var sum = 0;
                for (var i = 0; i < d.length; i += 4) { sum += (0.2126 * d[i] + 0.7152 * d[i + 1] + 0.0722 * d[i + 2]) / 255; }
                luma = sum / (d.length / 4);
            } catch (e) { /* картинка недоступна для чтения: остается средняя яркость */ }
            applyTint();
        };
        img.src = m[1];
    }

    if (root.dataset.mode === 'auto' && window.matchMedia) {
        matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
            root.dataset.theme = e.matches ? 'dark' : 'light';
            applyTint();
        });
    }
    measure();



    // ----- Переключатель светлая/темная тема в меню: меняется сразу и запоминается -----
    var themeBtn = document.getElementById('themeToggle');
    if (themeBtn) {
        var labelEl = themeBtn.querySelector('.theme-label');
        function paintThemeLabel() {
            if (labelEl) { labelEl.textContent = resolvedTheme() === 'dark' ? themeBtn.dataset.lDark : themeBtn.dataset.lLight; }
        }
        paintThemeLabel();
        themeBtn.addEventListener('click', function () {
            var mode = resolvedTheme() === 'dark' ? 'LIGHT' : 'DARK';
            root.dataset.mode = mode.toLowerCase();
            root.dataset.theme = mode === 'DARK' ? 'dark' : 'light';
            paintThemeLabel();
            applyTint();
            var token = document.querySelector('meta[name="_csrf"]');
            var header = document.querySelector('meta[name="_csrf_header"]');
            var headers = {'Content-Type': 'application/x-www-form-urlencoded'};
            if (token && header && token.content && header.content) { headers[header.content] = token.content; }
            fetch('/settings/mode', {method: 'POST', headers: headers, body: 'mode=' + mode});
        });
    }

    // ----- Аватары с инициалами: цвет зависит от имени -----
    function initials(name) {
        var parts = name.trim().split(/\s+/);
        return ((parts[0] || '')[0] || '') + ((parts[1] || '')[0] || '');
    }
    function colorOf(name) {
        var h = 0;
        for (var i = 0; i < name.length; i++) { h = (h * 31 + name.charCodeAt(i)) >>> 0; }
        return 'c' + (h % 8 + 1);
    }
    function paintAvatar(el, name) {
        el.textContent = initials(name).toUpperCase();
        el.classList.add(colorOf(name));
    }
    function enhance(scope) {
        (scope || document).querySelectorAll('.av[data-name]').forEach(function (el) {
            if (!el.dataset.done) { el.dataset.done = '1'; paintAvatar(el, el.dataset.name); }
        });
        (scope || document).querySelectorAll('td.owners[data-owners]').forEach(function (td) {
            if (td.classList.contains('has-avatars')) { return; }
            var names = td.dataset.owners.split(',').map(function (n) { return n.trim(); }).filter(Boolean);
            if (!names.length) { return; }
            var box = document.createElement('span');
            box.className = 'avatars';
            names.forEach(function (n) {
                var av = document.createElement('span');
                av.className = 'av';
                av.title = n;
                paintAvatar(av, n);
                box.appendChild(av);
            });
            var text = document.createElement('span');
            text.className = 'owners-text';
            text.textContent = td.textContent;
            td.textContent = '';
            td.title = names.join(', ');
            td.appendChild(box);
            td.appendChild(text);
            td.classList.add('has-avatars');
        });
    }
    // Фильтр языка в карточке SKU (Все / RU / EN) становится живым сегментным переключателем
    function enhanceSegments() {
        document.querySelectorAll('.lang-filter:not(.seg-row)').forEach(function (g) {
            g.classList.add('seg-row');
            g.querySelectorAll('button').forEach(function (b) { b.classList.add('seg-opt'); });
        });
    }
    enhanceSegments();
    enhance();
    document.body.addEventListener('htmx:afterSwap', function (e) { enhance(e.detail.target); enhanceSegments(); });

    // ----- Поиск: подсказка "/" и название раздела справа -----
    var searchBox = document.querySelector('.hub-search');
    var searchInput = document.querySelector('.hub-search-input');
    if (searchBox && searchInput) {
        var kbd = document.createElement('kbd');
        kbd.textContent = '/';
        searchBox.appendChild(kbd);
        document.addEventListener('keydown', function (e) {
            if (e.key !== '/' || e.metaKey || e.ctrlKey || e.altKey) { return; }
            var t = e.target;
            if (t && (/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || t.isContentEditable)) { return; }
            e.preventDefault();
            searchInput.focus();
        });
    }
    // ----- Каталог: семейства сегментным переключателем над фильтрами -----
    var familySelect = document.querySelector('#filters select[name="family"]');
    if (familySelect) {
        var wrap = document.createElement('div');
        wrap.className = 'seg-row family-seg';
        var opts = [['', 'Все']].concat([].slice.call(familySelect.options).filter(function (o) { return o.value; }).map(function (o) { return [o.value, o.textContent]; }));
        opts.forEach(function (o) {
            var label = document.createElement('label');
            label.className = 'seg-opt';
            var input = document.createElement('input');
            input.type = 'radio';
            input.name = '_family_seg';
            input.value = o[0];
            input.checked = familySelect.value === o[0];
            var span = document.createElement('span');
            span.textContent = o[1];
            label.appendChild(input);
            label.appendChild(span);
            wrap.appendChild(label);
        });
        wrap.addEventListener('change', function (e) {
            familySelect.value = e.target.value;
            familySelect.dispatchEvent(new Event('change', {bubbles: true}));
        });
        familySelect.classList.add('has-seg');
        var filters = document.getElementById('filters');
        // семейства и кнопки действий страницы (Создать SKU и т.п.) стоят в одной строке
        var toolbar = document.createElement('div');
        toolbar.className = 'seg-toolbar';
        filters.parentNode.insertBefore(toolbar, filters);
        toolbar.appendChild(wrap);
        var head = document.querySelector('.page-head');
        if (head) {
            var action = [].slice.call(head.children).filter(function (c) { return c.tagName !== 'H1' && !c.querySelector('h1'); }).pop();
            if (action) { toolbar.appendChild(action); }
        }
    }

    // ----- Предпросмотр на странице настроек: меняется сразу, сохраняется кнопкой -----
    var form = document.getElementById('settingsForm');
    if (!form) { return; }
    function val(name) {
        var el = form.querySelector('[name="' + name + '"]:checked') || form.querySelector('input[name="' + name + '"][type=range]');
        return el ? el.value : null;
    }
    function preview() {
        var mode = val('colorMode');
        if (mode) {
            root.dataset.mode = mode.toLowerCase();
            root.dataset.theme = mode === 'DARK' || (mode === 'AUTO' && matchMedia('(prefers-color-scheme: dark)').matches) ? 'dark' : 'light';
        }
        var palette = val('palette');
        if (palette) { root.dataset.palette = palette.toLowerCase(); }
        var bg = val('background');
        if (bg) {
            root.dataset.bgtype = bg.toLowerCase();
            var box = document.getElementById('wallBox');
            if (box) { box.hidden = bg !== 'PHOTO'; }
            var up = document.getElementById('wallUpload');
            if (up) { up.hidden = bg !== 'PHOTO'; }
        }
        var wall = val('wallpaper');
        if (wall) {
            var span = form.querySelector('input[name="wallpaper"]:checked + span');
            if (span) { root.style.setProperty('--wall', span.style.backgroundImage); }
        }
        root.style.setProperty('--wall-blur', val('wallBlur') + 'px');
        root.style.setProperty('--dim', (val('wallDim') / 100).toFixed(2));
        if (wall) { measure(); } else { applyTint(); }
    }
    form.addEventListener('input', function (e) {
        var out = e.target.type === 'range' && e.target.parentNode.querySelector('output');
        if (out) { out.textContent = e.target.value + (e.target.name === 'wallDim' ? '%' : ''); }
        preview();
    });
    var wallFile = document.getElementById('wallFile');
    if (wallFile) { wallFile.addEventListener('change', function () { if (wallFile.files.length) { wallFile.form.submit(); } }); }
    form.addEventListener('change', function (e) {
        if (e.target.name === 'uiTheme') {
            // переключение вида применяется после сохранения: меняется набор стилей
            var modern = form.querySelector('[name="uiTheme"]:checked').value === 'MODERN';
            document.getElementById('lookModern').hidden = !modern;
        }
    });
})();
