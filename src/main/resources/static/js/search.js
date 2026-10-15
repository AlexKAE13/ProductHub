// Мгновенный поиск в шапке: показ и скрытие выпадающего списка, стрелки, Esc, клавиша "/".
(function () {
    var box = document.querySelector('.hub-search');
    if (!box) {
        return;
    }
    var input = box.querySelector('.hub-search-input');
    var drop = box.querySelector('.hub-search-drop');

    function sync() {
        drop.hidden = drop.innerHTML.trim() === '' || document.activeElement !== input && !box.contains(document.activeElement);
    }

    document.body.addEventListener('htmx:afterSwap', function (e) {
        if (e.target === drop) {
            drop.hidden = drop.innerHTML.trim() === '';
        }
    });
    input.addEventListener('focus', function () {
        drop.hidden = drop.innerHTML.trim() === '';
    });
    document.addEventListener('click', function (e) {
        if (!box.contains(e.target)) {
            drop.hidden = true;
        }
    });
    box.addEventListener('keydown', function (e) {
        var hits = Array.prototype.slice.call(drop.querySelectorAll('.sr-hit'));
        var at = hits.indexOf(document.activeElement);
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
