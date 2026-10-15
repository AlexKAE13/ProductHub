// Фильтры-списки: подпись поля вместо пустого пункта, кнопка сброса, зависимость "линейка" от "семейства".
(function () {
    function sync(select) {
        var on = select.value !== '';
        select.classList.toggle('on', on);
        if (select.parentNode.classList.contains('sel')) {
            select.parentNode.classList.toggle('has-value', on);
        }
    }

    function enhance(select) {
        if (select.dataset.enhanced || select.hasAttribute('data-plain')) {
            return;
        }
        select.dataset.enhanced = '1';
        var wrap = document.createElement('span');
        wrap.className = 'sel';
        select.parentNode.insertBefore(wrap, select);
        wrap.appendChild(select);
        var clear = document.createElement('button');
        clear.type = 'button';
        clear.className = 'sel-clear';
        clear.textContent = '\u00d7';
        clear.title = 'Сбросить';
        clear.setAttribute('aria-label', 'Сбросить');
        clear.addEventListener('click', function () {
            select.value = '';
            select.dispatchEvent(new Event('change', {bubbles: true}));
        });
        wrap.appendChild(clear);
        select.addEventListener('change', function () {
            sync(select);
        });
        sync(select);
    }

    // В списке линеек остаются только линейки выбранного семейства
    function linkFamilyAndLine(form) {
        var family = form.querySelector('select[name=family]');
        var line = form.querySelector('select[name=line]');
        if (!family || !line) {
            return;
        }
        var all = Array.prototype.map.call(line.options, function (o) {
            return {value: o.value, text: o.text, family: o.getAttribute('data-family') || ''};
        });
        function rebuild() {
            var wanted = family.value;
            var current = line.value;
            while (line.options.length) {
                line.remove(0);
            }
            all.forEach(function (o) {
                if (o.value !== '' && wanted !== '' && o.family !== wanted) {
                    return;
                }
                var option = new Option(o.text, o.value);
                if (o.value === '') {
                    option.hidden = true;
                }
                line.add(option);
            });
            line.value = current;
            if (line.value !== current) {
                line.value = '';
            }
            sync(line);
        }
        family.addEventListener('change', rebuild);
        rebuild();
    }

    function init() {
        document.querySelectorAll('.filters').forEach(function (form) {
            form.querySelectorAll('select').forEach(enhance);
            linkFamilyAndLine(form);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
