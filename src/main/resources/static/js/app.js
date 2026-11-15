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

// В каталоге открываем карточку по клику по свободному месту строки.
document.addEventListener('click', function (e) {
    if (e.defaultPrevented || e.button !== 0 || e.target.closest('a, button, input, select, textarea, label')) { return; }
    if (window.getSelection && String(window.getSelection())) { return; }
    var row = e.target.closest('.catalog-row[data-href]');
    if (row) { window.location.assign(row.getAttribute('data-href')); }
});

document.addEventListener('DOMContentLoaded', function () {
    var choice = document.getElementById('category-choice');
    var input = document.getElementById('category');
    if (!choice || !input) { return; }
    if (input.value && !choice.value) { choice.value = '__other__'; }
    function updateCategory() {
        var other = choice.value === '__other__';
        input.hidden = !other;
        input.required = other;
        if (!other) { input.value = choice.value; }
        if (other) { input.focus(); }
    }
    choice.addEventListener('change', updateCategory);
    input.hidden = choice.value !== '__other__';
    input.required = choice.value === '__other__';
});
