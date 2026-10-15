// Карточка SKU: режим правки включается кнопкой "Править" в шапке. Пока он выключен, кнопки правки и публикации скрыты.
(function () {
    var main = document.querySelector('.content.card');
    var toggle = document.getElementById('editToggle');
    if (!main || !toggle) {
        return;
    }
    var key = 'hub-editing:' + location.pathname;

    function render(on) {
        main.classList.toggle('editing', on);
        toggle.textContent = on ? toggle.getAttribute('data-l-done') : toggle.getAttribute('data-l-edit');
        toggle.setAttribute('aria-pressed', on ? 'true' : 'false');
        try { sessionStorage.setItem(key, on ? '1' : '0'); } catch (e) { /* хранилище недоступно: режим просто не запоминается */ }
    }

    toggle.addEventListener('click', function () {
        render(!main.classList.contains('editing'));
    });
    // после сохранения блока (замена через HTMX) режим остается включенным; обновление страницы его тоже помнит
    var saved = false;
    try { saved = sessionStorage.getItem(key) === '1'; } catch (e) { /* без хранилища начинаем с выключенного */ }
    render(saved);
})();
