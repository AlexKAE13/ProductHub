/* Блок "Вопросы и правки": поиск по FAQ (виден, когда в FAQ больше 5 вопросов) и поле, растущее по мере набора. */
(function () {
    function norm(s) { return (s || '').toLowerCase().replace(/ё/g, 'е'); }

    function initSearch(root) {
        var input = root.querySelector('[data-qa-search]');
        if (!input || input.dataset.ready) return;
        input.dataset.ready = '1';
        var cards = root.querySelectorAll('details.qa-done');
        var none = root.querySelector('.qa-none');
        input.addEventListener('input', function () {
            var q = norm(input.value.trim());
            var shown = 0;
            cards.forEach(function (c) {
                var ok = !q || norm(c.textContent).indexOf(q) >= 0;
                c.hidden = !ok;
                if (ok) { shown++; if (q) c.open = true; }
            });
            if (none) none.hidden = shown > 0;
        });
    }

    function grow(t) {
        t.style.height = '';
        if (!t.value) return;
        t.style.height = Math.min(Math.max(t.scrollHeight + 2, 56), 320) + 'px';
    }

    function init(scope) {
        (scope || document).querySelectorAll('[data-qa]').forEach(initSearch);
        (scope || document).querySelectorAll('textarea[data-autogrow]').forEach(function (t) {
            if (t.dataset.ready) return;
            t.dataset.ready = '1';
            t.addEventListener('input', function () { grow(t); });
            grow(t);
        });
    }

    document.addEventListener('DOMContentLoaded', function () { init(document); });
    document.body && document.body.addEventListener('htmx:afterSwap', function (e) { init(e.target); });
    document.addEventListener('htmx:afterSettle', function () { init(document); });
})();
