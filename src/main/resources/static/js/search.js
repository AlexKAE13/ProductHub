// Мгновенный поиск в шапке: показ и скрытие выпадающего списка, стрелки, Esc, клавиша "/".
(function () {
    var box = document.querySelector('.hub-search');
    if (!box) {
        return;
    }
    var input = box.querySelector('.hub-search-input');
    var drop = box.querySelector('.hub-search-drop');

    // ---- Недавно открытое из поиска: последние 5 переходов, хранятся в браузере пользователя ----
    var KEY = 'hub.recentSearch';
    function loadRecent() {
        try { return JSON.parse(localStorage.getItem(KEY) || '[]').slice(0, 5); } catch (e) { return []; }
    }
    function saveRecent(list) {
        try { localStorage.setItem(KEY, JSON.stringify(list.slice(0, 5))); } catch (e) { /* хранилище недоступно */ }
    }
    function remember(a) {
        var title = a.querySelector('.sr-title');
        if (!title) { return; }
        var sub = a.querySelector('.sr-sub');
        var item = {title: title.textContent.trim(), sub: sub ? sub.textContent.trim() : '', href: a.getAttribute('href')};
        var list = loadRecent().filter(function (r) { return r.href !== item.href && !(r.title === item.title && r.sub === item.sub); });
        list.unshift(item);
        saveRecent(list);
    }
    document.addEventListener('click', function (e) {
        var a = e.target.closest && e.target.closest('a.sr-hit');
        if (a) { remember(a); }
    });
    function showRecent() {
        var list = loadRecent();
        if (!list.length) { drop.innerHTML = ''; drop.hidden = true; return; }
        drop.textContent = '';
        var wrap = document.createElement('div');
        wrap.className = 'sr sr-recent';
        var head = document.createElement('div');
        head.className = 'sr-head';
        var h = document.createElement('span');
        h.textContent = box.getAttribute('data-l-recent') || '';
        var clear = document.createElement('button');
        clear.type = 'button';
        clear.className = 'link-btn sr-clear';
        clear.textContent = box.getAttribute('data-l-clear') || '';
        clear.addEventListener('mousedown', function (e) {
            e.preventDefault();
            saveRecent([]);
            drop.innerHTML = '';
            drop.hidden = true;
        });
        head.appendChild(h);
        head.appendChild(clear);
        wrap.appendChild(head);
        list.forEach(function (r) {
            var a = document.createElement('a');
            a.className = 'sr-hit';
            a.href = r.href;
            var t = document.createElement('span');
            t.className = 'sr-title';
            t.textContent = r.title;
            a.appendChild(t);
            if (r.sub) {
                var sub = document.createElement('span');
                sub.className = 'sr-sub';
                sub.textContent = r.sub;
                a.appendChild(sub);
            }
            wrap.appendChild(a);
        });
        drop.appendChild(wrap);
        drop.hidden = false;
    }
    function isRecentShown() { return !!drop.querySelector('.sr-recent'); }

    function sync() {
        drop.hidden = drop.innerHTML.trim() === '' || document.activeElement !== input && !box.contains(document.activeElement);
    }

    document.body.addEventListener('htmx:afterSwap', function (e) {
        if (e.target === drop) {
            if (!input.value.trim()) { showRecent(); return; }
            drop.hidden = drop.innerHTML.trim() === '';
        }
    });
    input.addEventListener('focus', function () {
        if (!input.value.trim()) { showRecent(); return; }
        drop.hidden = drop.innerHTML.trim() === '';
    });
    input.addEventListener('click', function () {
        if (!input.value.trim() && drop.hidden) { showRecent(); }
    });
    input.addEventListener('input', function () {
        if (!input.value.trim()) { showRecent(); }
        else if (isRecentShown()) { drop.innerHTML = ''; drop.hidden = true; }
    });
    document.addEventListener('click', function (e) {
        if (!box.contains(e.target)) {
            drop.hidden = true;
        }
    });
    // Одна подсветка: от стрелок или от курсора. Побеждает тот, кто двигался последним (курсор мыши - в приоритете при наведении).
    var lastX = -1, lastY = -1;
    drop.addEventListener('mousemove', function (e) {
        if (e.clientX === lastX && e.clientY === lastY) { return; }
        lastX = e.clientX;
        lastY = e.clientY;
        drop.classList.remove('mode-kbd');
        drop.classList.add('mode-mouse');
        var a = document.activeElement;
        if (a && a.classList && a.classList.contains('sr-hit')) { input.focus(); }
    });
    box.addEventListener('keydown', function (e) {
        var hits = Array.prototype.slice.call(drop.querySelectorAll('.sr-hit'));
        var at = hits.indexOf(document.activeElement);
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
            // стрелки продолжают с того места, где стоит курсор мыши
            if (at < 0) {
                at = hits.findIndex(function (h) { return h.matches(':hover'); });
            }
            drop.classList.remove('mode-mouse');
            drop.classList.add('mode-kbd');
        }
        if (e.key === 'Escape') {
            drop.hidden = true;
            input.focus();
        } else if (e.key === 'ArrowDown' && hits.length) {
            e.preventDefault();
            hits[Math.min(at + 1, hits.length - 1)].focus();
        } else if (e.key === 'ArrowUp' && hits.length) {
            e.preventDefault();
            if (at <= 0) {
                input.focus();
            } else {
                hits[at - 1].focus();
            }
        }
    });
    document.addEventListener('keydown', function (e) {
        var tag = (document.activeElement && document.activeElement.tagName) || '';
        if (e.key === '/' && !/INPUT|TEXTAREA|SELECT/.test(tag) && !e.ctrlKey && !e.metaKey) {
            e.preventDefault();
            input.focus();
        }
    });
})();
