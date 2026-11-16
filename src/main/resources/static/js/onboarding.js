(function () {
    var panel = document.getElementById('home-onboarding');
    if (!panel) { return; }
    var steps = Array.prototype.slice.call(panel.querySelectorAll('[data-onboarding-step]'));
    var content = panel.querySelector('[data-onboarding-content]');
    var close = panel.querySelector('[data-onboarding-close]');
    var open = panel.querySelector('[data-onboarding-open]');
    var controls = panel.querySelector('[data-onboarding-controls]');
    var progress = panel.querySelector('[data-onboarding-progress]');
    var prev = panel.querySelector('[data-onboarding-prev]');
    var next = panel.querySelector('[data-onboarding-next]');
    var key = 'producthub.onboarding.v1.' + panel.getAttribute('data-user');
    var index = 0;
    var dismissed = false;
    try { dismissed = localStorage.getItem(key) === 'closed'; } catch (e) { /* Хранилище может быть недоступно. */ }

    function paint() {
        steps.forEach(function (step, i) { step.hidden = i !== index; });
        progress.textContent = progress.getAttribute('data-label').replace('{0}', index + 1).replace('{1}', steps.length);
        prev.hidden = index === 0;
        next.textContent = next.getAttribute(index === steps.length - 1 ? 'data-finish' : 'data-next');
    }
    function setClosed(value) {
        content.hidden = value;
        close.hidden = value;
        open.hidden = !value;
        try { if (value) { localStorage.setItem(key, 'closed'); } else { localStorage.removeItem(key); } } catch (e) { /* Без сохранения настройки. */ }
    }
    prev.addEventListener('click', function () { if (index > 0) { index--; paint(); } });
    next.addEventListener('click', function () {
        if (index === steps.length - 1) { setClosed(true); open.focus(); }
        else { index++; paint(); }
    });
    close.addEventListener('click', function () { setClosed(true); open.focus(); });
    open.addEventListener('click', function () { index = 0; paint(); setClosed(false); close.focus(); });
    controls.hidden = false;
    paint();
    setClosed(dismissed);
})();
