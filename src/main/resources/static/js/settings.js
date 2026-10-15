// Страница "Настройки": кнопка "Сохранить" появляется сбоку от блока, на строке, где вы только что что-то изменили.
(function () {
    var form = document.getElementById('settingsForm');
    if (!form) { return; }
    form.style.position = 'relative';

    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'primary save-float';
    button.textContent = form.dataset.saveLabel || 'Сохранить';
    button.hidden = true;
    form.appendChild(button);

    function snapshot() {
        return new URLSearchParams(new FormData(form)).toString();
    }
    var initial = snapshot();
    var lastRow = null;

    function place(row) {
        var f = form.getBoundingClientRect();
        var r = row.getBoundingClientRect();
        var width = button.offsetWidth || 120;
        var roomRight = window.innerWidth - f.right;
        if (roomRight >= width + 24) {
            button.classList.remove('save-fixed');
            button.style.top = Math.max(0, r.top - f.top + (r.height - 34) / 2) + 'px';
            button.style.left = (f.width + 14) + 'px';
        } else {
            // места сбоку нет (узкое окно): кнопка прижимается к правому краю экрана на той же высоте
            button.classList.add('save-fixed');
            button.style.top = Math.min(Math.max(70, r.top + (r.height - 34) / 2), window.innerHeight - 120) + 'px';
            button.style.left = '';
        }
    }

    function refresh(target) {
        var dirty = snapshot() !== initial;
        if (!dirty) { button.hidden = true; return; }
        var row = target && target.closest ? (target.closest('.setting') || target.closest('.themes') || target.closest('.block')) : null;
        lastRow = row || lastRow;
        var wasHidden = button.hidden;
        button.hidden = false;
        if (lastRow) { place(lastRow); }
        if (wasHidden) {
            button.classList.remove('pop');
            void button.offsetWidth;
            button.classList.add('pop');
        }
    }

    form.addEventListener('input', function (e) { refresh(e.target); });
    form.addEventListener('change', function (e) { refresh(e.target); });
    window.addEventListener('resize', function () { if (!button.hidden && lastRow) { place(lastRow); } });
    window.addEventListener('scroll', function () { if (!button.hidden && button.classList.contains('save-fixed') && lastRow) { place(lastRow); } }, {passive: true});
    button.addEventListener('click', function () { form.requestSubmit ? form.requestSubmit() : form.submit(); });
})();
