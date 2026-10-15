// HTMX передает CSRF-токен в заголовке (SPEC.md 8.2).
document.addEventListener('htmx:configRequest', function (event) {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header && token.content && header.content) {
        event.detail.headers[header.content] = token.content;
    }
});


// Форма с data-confirm спрашивает подтверждение перед отправкой (удаление и другие необратимые действия).
document.addEventListener('submit', function (e) {
    var text = e.target && e.target.getAttribute && e.target.getAttribute('data-confirm');
    if (text && !window.confirm(text)) {
        e.preventDefault();
    }
}, true);

// Кнопка с data-confirm (когда вложенную форму сделать нельзя) тоже спрашивает подтверждение перед отправкой.
document.addEventListener('click', function (e) {
    var b = e.target.closest && e.target.closest('button[data-confirm]');
    if (b && !window.confirm(b.getAttribute('data-confirm'))) {
        e.preventDefault();
        e.stopPropagation();
    }
}, true);
