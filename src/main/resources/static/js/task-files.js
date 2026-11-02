(function () {
    var input = document.getElementById('taskFile');
    if (!input) return;
    var list = input.form.querySelector('.task-file-names');
    var submit = input.form.querySelector('.task-file-submit');
    submit.hidden = !input.files.length;
    input.addEventListener('change', function () {
        submit.hidden = !input.files.length;
        list.replaceChildren();
        Array.from(input.files).forEach(function (file) {
            var item = document.createElement('li');
            item.textContent = file.name;
            list.appendChild(item);
        });
    });
})();
