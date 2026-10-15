// Загрузка файлов перетаскиванием: файлы попадают в обычное поле выбора и отправляются формой.
(function () {
    var zone = document.getElementById('dropzone');
    var input = document.getElementById('upload-input');
    var list = document.getElementById('upload-list');
    if (!zone || !input) {
        return;
    }
    function show() {
        list.innerHTML = '';
        Array.prototype.forEach.call(input.files, function (f) {
            var li = document.createElement('li');
            li.textContent = f.name + ' (' + Math.round(f.size / 1024) + ' КБ)';
            list.appendChild(li);
        });
    }
    ['dragenter', 'dragover'].forEach(function (name) {
        zone.addEventListener(name, function (e) {
            e.preventDefault();
            zone.classList.add('over');
        });
    });
    ['dragleave', 'drop'].forEach(function (name) {
        zone.addEventListener(name, function (e) {
            e.preventDefault();
            zone.classList.remove('over');
        });
    });
    zone.addEventListener('drop', function (e) {
        if (e.dataTransfer && e.dataTransfer.files.length) {
            input.files = e.dataTransfer.files;
            show();
        }
    });
    input.addEventListener('change', show);
})();
