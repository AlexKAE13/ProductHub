// Канбан: перетаскивание карточек между колонками (SortableJS), смена статуса отправляется на сервер.
(function () {
    function csrfHeaders() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var headers = {'Content-Type': 'application/x-www-form-urlencoded'};
        if (token && header && token.content && header.content) {
            headers[header.content] = token.content;
        }
        return headers;
    }

    function showMessage(text) {
        var box = document.getElementById('board-message');
        if (box) {
            box.textContent = text;
            box.hidden = !text;
        }
    }

    function updateCounts() {
        document.querySelectorAll('.kanban .column').forEach(function (column) {
            var count = column.querySelector('.count');
            if (count) {
                count.textContent = column.querySelectorAll('.task-card').length;
            }
        });
    }

    function init() {
        if (typeof Sortable === 'undefined') {
            return;
        }
        document.querySelectorAll('.kanban .cards').forEach(function (list) {
            if (list.dataset.sortable) {
                return;
            }
            list.dataset.sortable = '1';
            // зона приема карточки - вся колонка, включая цветную шапку: список тянется на всю высоту и под шапку
            var column = list.closest('.column');
            if (column) {
                var cs = getComputedStyle(column);
                var padX = parseFloat(cs.paddingLeft) || 0;
                var padBottom = parseFloat(cs.paddingBottom) || 0;
                var up = list.getBoundingClientRect().top - column.getBoundingClientRect().top;
                list.style.margin = '-' + up + 'px -' + padX + 'px -' + padBottom + 'px';
                list.style.padding = up + 'px ' + padX + 'px ' + padBottom + 'px';
            }
            Sortable.create(list, {
                emptyInsertThreshold: 40,
                group: 'tasks',
                onStart: function () { document.getElementById('board').classList.add('is-dragging'); },
                onMove: function (evt) {
                    document.querySelectorAll('.kanban .column.drop-over').forEach(function (c) { c.classList.remove('drop-over'); });
                    var col = evt.to.closest('.column');
                    if (col) { col.classList.add('drop-over'); }
                },
                animation: 120,
                draggable: '.task-card.movable',
                ghostClass: 'dragging',
                // в современной теме карточка в руке слегка наклонена и приподнята (как в макете)
                forceFallback: document.body.classList.contains('theme-modern'),
                fallbackClass: 'task-drag',
                fallbackOnBody: true,
                onEnd: function (evt) {
                    document.getElementById('board').classList.remove('is-dragging');
                    document.querySelectorAll('.kanban .column.drop-over').forEach(function (c) { c.classList.remove('drop-over'); });
                    var from = evt.from.getAttribute('data-status');
                    var to = evt.to.getAttribute('data-status');
                    if (from === to) {
                        return;
                    }
                    var id = evt.item.getAttribute('data-id');
                    fetch('/tasks/' + id + '/status', {method: 'POST', headers: csrfHeaders(), body: 'status=' + to})
                        .then(function (response) {
                            if (response.ok) {
                                showMessage('');
                                updateCounts();
                                return null;
                            }
                            return response.text().then(function (text) {
                                // возвращаем карточку на место и показываем причину
                                evt.from.insertBefore(evt.item, evt.from.children[evt.oldIndex] || null);
                                updateCounts();
                                showMessage(response.status === 409 ? text : 'Не удалось изменить статус задачи');
                            });
                        })
                        .catch(function () {
                            evt.from.insertBefore(evt.item, evt.from.children[evt.oldIndex] || null);
                            updateCounts();
                            showMessage('Нет связи с сервером');
                        });
                }
            });
        });
    }

    document.addEventListener('DOMContentLoaded', init);
    document.body.addEventListener('htmx:afterSwap', init);
    if (document.readyState !== 'loading') {
        init();
    }
})();
