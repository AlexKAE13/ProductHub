/* Поля со списком артикулов (data-suggest="sku" без data-single): каждый SKU становится плашкой "WEP-30L x".
   Сам исходный элемент остается в форме скрытым и хранит значение через запятую, поэтому сервер ничего не замечает.
   Ввод: Enter, запятая или ; превращают набранное в плашку, Backspace в пустом поле убирает последнюю,
   вставка списка разбивается на плашки, подсказки работают как раньше (suggest.js). */
(function () {
    var SPLIT = /[,;\n]+/;

    function parse(value) {
        var seen = {};
        return (value || '').split(SPLIT).map(function (s) { return s.trim(); }).filter(function (s) {
            var k = s.toLowerCase();
            if (!s || seen[k]) { return false; }
            seen[k] = true;
            return true;
        });
    }

    function enhance(orig) {
        if (orig.dataset.chips) { return; }
        orig.dataset.chips = '1';
        var chips = parse(orig.value);
        var required = orig.required;
        var disabled = orig.disabled;
        var field = document.createElement('div');
        field.className = 'chips-field' + (disabled ? ' is-disabled' : '');
        var input = document.createElement('input');
        input.type = 'text';
        input.className = 'chips-input';
        input.setAttribute('data-suggest', 'sku');
        input.setAttribute('data-single', '');
        input.autocomplete = 'off';
        input.disabled = disabled;
        var label = orig.getAttribute('aria-label');
        if (label) { input.setAttribute('aria-label', label); }
        var placeholder = orig.getAttribute('placeholder') || 'SKU';

        // исходный элемент прячем и снимаем с него required (скрытое обязательное поле блокирует отправку формы)
        orig.hidden = true;
        orig.required = false;
        orig.parentNode.insertBefore(field, orig.nextSibling);

        function sync(fire) {
            orig.value = chips.join(', ');
            input.required = required && chips.length === 0;
            input.placeholder = chips.length ? '' : placeholder;
            if (fire) {
                orig.dispatchEvent(new Event('input', {bubbles: true}));
                orig.dispatchEvent(new Event('change', {bubbles: true}));
            }
        }

        function render() {
            field.querySelectorAll('.chip').forEach(function (c) { c.remove(); });
            chips.forEach(function (text, i) {
                var chip = document.createElement('span');
                chip.className = 'chip';
                var t = document.createElement('span');
                t.textContent = text;
                chip.appendChild(t);
                if (!disabled) {
                    var x = document.createElement('button');
                    x.type = 'button';
                    x.className = 'chip-x';
                    x.setAttribute('aria-label', '×');
                    x.textContent = '×';
                    x.addEventListener('click', function (e) {
                        e.stopPropagation();
                        chips.splice(i, 1);
                        render();
                        sync(true);
                        input.focus();
                    });
                    chip.appendChild(x);
                }
                field.insertBefore(chip, input);
            });
        }

        function add(text) {
            var added = false;
            parse(text).forEach(function (s) {
                if (!chips.some(function (c) { return c.toLowerCase() === s.toLowerCase(); })) {
                    chips.push(s);
                    added = true;
                }
            });
            input.value = '';
            if (added) { render(); sync(true); }
        }

        field.appendChild(input);
        render();
        sync(false);

        field.addEventListener('mousedown', function (e) {
            if (e.target === field) { e.preventDefault(); input.focus(); }
        });
        input.addEventListener('keydown', function (e) {
            var dropOpen = document.querySelector('.suggest-drop:not([hidden])');
            if (e.key === 'Enter') {
                if (dropOpen) { return; }          // Enter выбирает подсказку (suggest.js)
                e.preventDefault();
                if (input.value.trim()) { add(input.value); }
            } else if (e.key === ',' || e.key === ';') {
                e.preventDefault();
                if (input.value.trim()) { add(input.value); }
            } else if (e.key === 'Backspace' && !input.value && chips.length) {
                chips.pop();
                render();
                sync(true);
            }
        });
        // подсказка выбрана или поле потеряло фокус - набранное становится плашкой
        input.addEventListener('change', function () { if (input.value.trim()) { add(input.value); } });
        input.addEventListener('paste', function (e) {
            var text = (e.clipboardData || window.clipboardData).getData('text');
            if (SPLIT.test(text)) {
                e.preventDefault();
                add(input.value + ',' + text);
            }
        });
    }

    function init(scope) {
        (scope || document).querySelectorAll('[data-suggest="sku"]:not([data-single]):not([data-chips])').forEach(enhance);
    }

    document.addEventListener('DOMContentLoaded', function () { init(document); });
    document.addEventListener('htmx:afterSettle', function (e) { init(e.target); });
    if (document.readyState !== 'loading') { init(document); }
})();
