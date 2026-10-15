// Выбор строк списка и массовые действия.
// Панель появляется, когда выбрана хотя бы одна строка. Есть "выбрать все на странице", "снять выделение",
// выбор диапазона с Shift, переключение строки кликом по ячейке с галочкой, отмена по Esc и кнопки основных действий.
(function () {
    var lastIndex = null;

    function boxes() {
        return Array.prototype.slice.call(document.querySelectorAll('.bulk-check'));
    }

    function update() {
        var bar = document.getElementById('bulkBar');
        if (!bar) {
            return;
        }
        var all = boxes();
        var checked = all.filter(function (b) { return b.checked; });
        bar.classList.toggle('show', checked.length > 0);
        var count = document.getElementById('bulkCount');
        if (count) {
            count.textContent = checked.length;
        }
        all.forEach(function (b) {
            var row = b.closest('tr');
            if (row) { row.classList.toggle('selected', b.checked); }
        });
        var head = document.getElementById('bulkAll');
        if (head) {
            head.checked = all.length > 0 && checked.length === all.length;
            head.indeterminate = checked.length > 0 && checked.length < all.length;
        }
        var selectAll = document.getElementById('bulkSelectAll');
        if (selectAll) {
            selectAll.hidden = checked.length === all.length;
        }
    }

    function setAll(value) {
        boxes().forEach(function (b) { b.checked = value; });
        lastIndex = null;
        update();
    }

    // Показывает поле значения, подходящее выбранному действию: статусы и теги выбираются из списка
    function hint() {
        var action = document.getElementById('bulkAction');
        if (!action) {
            return;
        }
        document.querySelectorAll('#bulkBar [data-for]').forEach(function (control) {
            var active = control.getAttribute('data-for') === action.value;
            control.hidden = !active;
            control.disabled = !active;
        });
    }

    document.addEventListener('change', function (e) {
        var t = e.target;
        if (t.id === 'bulkAll') {
            setAll(t.checked);
            return;
        }
        if (t.classList && t.classList.contains('bulk-check')) {
            update();
        }
        if (t.id === 'bulkAction') {
            hint();
        }
    });

    document.addEventListener('click', function (e) {
        var t = e.target;
        // Shift + клик по галочке выбирает диапазон от прошлой отмеченной строки
        if (t.classList && t.classList.contains('bulk-check')) {
            var list = boxes();
            var index = list.indexOf(t);
            if (e.shiftKey && lastIndex !== null && lastIndex !== index) {
                var from = Math.min(lastIndex, index);
                var to = Math.max(lastIndex, index);
                for (var i = from; i <= to; i++) { list[i].checked = t.checked; }
                update();
            }
            lastIndex = index;
            return;
        }
        // Клик по свободному месту ячейки с галочкой тоже переключает строку (цель крупнее)
        var cell = t.closest && t.closest('td.bulk-cell');
        if (cell && t === cell) {
            var box = cell.querySelector('.bulk-check');
            if (box) { box.checked = !box.checked; lastIndex = boxes().indexOf(box); update(); }
            return;
        }
        if (t.closest && t.closest('#bulkSelectAll')) { setAll(true); return; }
        if (t.closest && t.closest('#bulkClear')) { setAll(false); return; }
        var quick = t.closest && t.closest('.bulk-quick');
        if (quick) {
            var confirmText = quick.getAttribute('data-confirm-text');
            if (confirmText && !window.confirm(confirmText.replace('{n}', document.querySelectorAll('.bulk-check:checked').length))) {
                return;
            }
            var form = document.getElementById('bulkForm');
            var parts = quick.getAttribute('data-quick').split(':');
            var more = document.getElementById('bulkMore');
            if (more) { more.disabled = true; }   // поля раздела "Другие действия" в этот запрос не попадают
            ['action', 'value'].forEach(function (name, i) {
                var hidden = form.querySelector('input[type=hidden][name="' + name + '"]');
                if (!hidden) {
                    hidden = document.createElement('input');
                    hidden.type = 'hidden';
                    hidden.name = name;
                    form.appendChild(hidden);
                }
                hidden.value = parts[i];
            });
            if (form.requestSubmit) { form.requestSubmit(); } else { form.submit(); }
        }
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && document.querySelector('.bulk-check:checked')) {
            var tag = e.target && e.target.tagName;
            if (tag === 'INPUT' && e.target.type === 'text') { return; }
            setAll(false);
        }
    });

    document.body.addEventListener('htmx:afterSwap', function () { lastIndex = null; update(); hint(); });
    update();
    hint();
})();
