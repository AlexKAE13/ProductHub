/* Чат своих досок: прокрутка к новым сообщениям, Enter отправляет, Shift+Enter - новая строка. */
(function () {
    var stick = new WeakMap();

    function atBottom(list) { return list.scrollHeight - list.scrollTop - list.clientHeight < 40; }

    function toBottom(list) { list.scrollTop = list.scrollHeight; }

    // перед обновлением запоминаем, был ли читатель внизу: если нет, не сбрасываем его прокрутку
    document.body.addEventListener('htmx:beforeSwap', function (e) {
        var t = e.detail && e.detail.target;
        if (t && t.classList && t.classList.contains('chat-list')) { stick.set(t.parentNode, atBottom(t)); }
    });
    document.body.addEventListener('htmx:afterSettle', function (e) {
        document.querySelectorAll('.chat-list').forEach(function (list) {
            var was = stick.get(list.parentNode);
            if (was === undefined || was) { toBottom(list); }
        });
    });
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.chat-list').forEach(toBottom);
    });
    document.addEventListener('keydown', function (e) {
        var t = e.target;
        if (e.key === 'Enter' && !e.shiftKey && !e.isComposing && t && t.matches && t.matches('.chat-form textarea')) {
            e.preventDefault();
            if (t.value.trim()) { t.form.requestSubmit(); }
        }
    });
})();
