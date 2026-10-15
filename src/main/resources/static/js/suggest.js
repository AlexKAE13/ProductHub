// Подсказки в полях ввода: артикулы (можно несколько через запятую) и линейки.
// Поле помечается атрибутом data-suggest="sku" или "line". Стрелки выбирают, Enter или Tab подставляют, Esc закрывает.
(function () {
    var drop = null;
    var current = null;
    var items = [];
    var active = -1;
    var timer = null;
    var seq = 0;

    function ensureDrop() {
        if (!drop) {
            drop = document.createElement('div');
            drop.className = 'suggest-drop';
            drop.hidden = true;
            drop.setAttribute('role', 'listbox');
            document.body.appendChild(drop);
            drop.addEventListener('mousedown', function (e) {
                var row = e.target.closest('.suggest-item');
                if (row) {
                    e.preventDefault();
                    pick(parseInt(row.getAttribute('data-i'), 10));
                }
            });
        }
        return drop;
    }

    function multi(input) { return input.getAttribute('data-suggest') === 'sku' && !input.hasAttribute('data-single'); }

    // Текущее слово: от последнего разделителя до курсора
    function token(input) {
        var value = input.value;
        var caret = input.selectionStart == null ? value.length : input.selectionStart;
        var start = 0;
        if (multi(input)) {
            for (var i = caret - 1; i >= 0; i--) {
                if (/[,;\n]/.test(value.charAt(i))) { start = i + 1; break; }
            }
        }
        return {start: start, end: caret, text: value.slice(start, caret).trim()};
    }

    function close() {
        if (drop) { drop.hidden = true; }
        items = [];
        active = -1;
    }

    function place(input) {
        var r = input.getBoundingClientRect();
        drop.style.left = Math.round(r.left + window.scrollX) + 'px';
        drop.style.top = Math.round(r.bottom + window.scrollY + 4) + 'px';
        drop.style.minWidth = Math.max(260, Math.round(r.width)) + 'px';
    }

    function render() {
        var d = ensureDrop();
        d.textContent = '';
        items.forEach(function (it, i) {
            var row = document.createElement('div');
            row.className = 'suggest-item' + (i === active ? ' active' : '');
            row.setAttribute('data-i', i);
            row.setAttribute('role', 'option');
            var v = document.createElement('strong');
            v.textContent = it.value;
            row.appendChild(v);
            if (it.hint) {
                var h = document.createElement('span');
                h.className = 'suggest-hint';
                h.textContent = it.hint;
                row.appendChild(h);
            }
            d.appendChild(row);
        });
        d.hidden = items.length === 0;
        if (current) { place(current); }
    }

    function pick(i) {
        var it = items[i];
        if (!it || !current) { return; }
        var t = token(current);
        var before = current.value.slice(0, t.start);
        var leading = before.length && !/\s$/.test(before) ? ' ' : '';
        var after = current.value.slice(t.end);
        var insert = it.value + (multi(current) ? ', ' : '');
        current.value = before + leading + insert + after.replace(/^\s+/, '');
        var pos = (before + leading + insert).length;
        current.setSelectionRange(pos, pos);
        current.dispatchEvent(new Event('input', {bubbles: true}));
        current.dispatchEvent(new Event('change', {bubbles: true}));
        close();
        current.focus();
    }

    function query(input) {
        var t = token(input);
        if (!t.text) { if (input.getAttribute('data-suggest') !== 'line') { close(); return; } }
        var kind = input.getAttribute('data-suggest') === 'line' ? 'lines' : 'skus';
        var mine = ++seq;
        fetch('/suggest/' + kind + '?q=' + encodeURIComponent(t.text), {headers: {'Accept': 'application/json'}, credentials: 'same-origin'})
            .then(function (r) { return r.ok ? r.json() : []; })
            .then(function (list) {
                if (mine !== seq || current !== input) { return; }
                // не предлагаем то, что уже введено целиком
                items = list.filter(function (it) { return it.value.toLowerCase() !== t.text.toLowerCase(); });
                active = items.length ? 0 : -1;
                render();
            })
            .catch(function () { close(); });
    }

    document.addEventListener('input', function (e) {
        var el = e.target;
        if (!el.hasAttribute || !el.hasAttribute('data-suggest')) { return; }
        current = el;
        clearTimeout(timer);
        timer = setTimeout(function () { query(el); }, 120);
    });

    document.addEventListener('focusin', function (e) {
        var el = e.target;
        if (el.hasAttribute && el.hasAttribute('data-suggest') && el.getAttribute('data-suggest') === 'line' && !el.value) {
            current = el;
            query(el);
        }
    });

    document.addEventListener('keydown', function (e) {
        if (!drop || drop.hidden || !current || e.target !== current) { return; }
        if (e.key === 'ArrowDown') { e.preventDefault(); active = (active + 1) % items.length; render(); }
        else if (e.key === 'ArrowUp') { e.preventDefault(); active = (active - 1 + items.length) % items.length; render(); }
        else if (e.key === 'Enter' || e.key === 'Tab') { if (active >= 0) { e.preventDefault(); pick(active); } }
        else if (e.key === 'Escape') { close(); }
    });

    document.addEventListener('focusout', function () { setTimeout(function () { if (document.activeElement !== current) { close(); } }, 120); });
    window.addEventListener('scroll', function () { if (drop && !drop.hidden && current) { place(current); } }, true);
    window.addEventListener('resize', close);
})();
