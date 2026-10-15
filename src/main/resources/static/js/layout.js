// Кнопка "свернуть сайдбар": состояние запоминается в личных настройках.
(function () {
    var button = document.getElementById('sidebarToggle');
    if (!button) {
        return;
    }
    button.addEventListener('click', function () {
        var collapsed = document.body.classList.toggle('sidebar-collapsed');
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var headers = {'Content-Type': 'application/x-www-form-urlencoded'};
        if (token && header && token.content && header.content) {
            headers[header.content] = token.content;
        }
        fetch('/settings/sidebar', {method: 'POST', headers: headers, body: 'collapsed=' + collapsed});
    });
})();


// Узкое окно (например, половина экрана ноутбука): боковое меню само сворачивается в иконки.
// Это временное состояние, оно не записывается в настройки; на широком окне меню возвращается как было.
(function () {
    var LIMIT = 1100;
    function apply() {
        var body = document.body;
        if (!body.classList.contains('layout-side')) {
            return;
        }
        var narrow = window.innerWidth < LIMIT;
        if (narrow && !body.classList.contains('sidebar-collapsed')) {
            body.classList.add('sidebar-collapsed');
            body.dataset.autoCollapsed = '1';
        } else if (!narrow && body.dataset.autoCollapsed === '1') {
            body.classList.remove('sidebar-collapsed');
            delete body.dataset.autoCollapsed;
        }
    }
    // ручное нажатие "свернуть" отменяет автоматику: выбор пользователя важнее
    var toggle = document.getElementById('sidebarToggle');
    if (toggle) {
        toggle.addEventListener('click', function () { delete document.body.dataset.autoCollapsed; }, true);
    }
    window.addEventListener('resize', apply);
    apply();
})();
