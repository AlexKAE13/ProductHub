// Комментарии прямо на странице: выбрать элемент, написать, увидеть метки и ответы коллег.
(function () {
    var root = document.getElementById('hub-feedback');
    if (!root) {
        return;
    }
    function label(name) {
        return root.getAttribute('data-l-' + name) || name;
    }
    var csrf = (document.querySelector('meta[name="_csrf"]') || {}).content;
    var csrfHeader = (document.querySelector('meta[name="_csrf_header"]') || {}).content;
    var KINDS = ['BUG', 'IDEA', 'QUESTION'];
    var comments = [];
    var picking = false;
    var highlight = null;

    // ---------- каркас интерфейса
    function el(tag, cls, text) {
        var node = document.createElement(tag);
        if (cls) {
            node.className = cls;
        }
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    var host = el('div', 'fb-host');
    host.id = 'fb-host';
    var toggle = el('button', 'fb-toggle', label('button'));
    toggle.type = 'button';
    var badge = el('span', 'fb-badge', '0');
    toggle.appendChild(badge);
    var panel = el('div', 'fb-panel');
    panel.hidden = true;
    host.appendChild(panel);
    host.appendChild(toggle);
    document.body.appendChild(host);
    var layer = el('div', 'fb-pins');
    layer.id = 'fb-pins';
    document.body.appendChild(layer);

    // ---------- сведения об элементе
    function cssPath(node) {
        var parts = [];
        while (node && node.nodeType === 1 && node !== document.body && parts.length < 8) {
            var part = node.tagName.toLowerCase();
            if (node.id && /^[A-Za-z][\w-]*$/.test(node.id)) {
                parts.unshift('#' + node.id);
                break;
            }
            var classes = Array.prototype.filter.call(node.classList, function (c) {
                return /^[A-Za-z][\w-]*$/.test(c) && !/^(on|active|has-value|fb-)/.test(c);
            }).slice(0, 2);
            if (classes.length) {
                part += '.' + classes.join('.');
            }
            var parent = node.parentElement;
            if (parent) {
                var same = Array.prototype.filter.call(parent.children, function (s) {
                    return s.tagName === node.tagName;
                });
                if (same.length > 1) {
                    part += ':nth-of-type(' + (same.indexOf(node) + 1) + ')';
                }
            }
            parts.unshift(part);
            node = parent;
        }
        return parts.join(' > ');
    }

    function squash(text, max) {
        return (text || '').replace(/\s+/g, ' ').trim().slice(0, max);
    }

    function sectionTitle(node) {
        var holder = node.closest('section, article, form, .block, details');
        while (holder) {
            var head = holder.querySelector('h1, h2, h3, caption, summary');
            if (head && !head.contains(node) || head === node) {
                return squash(head.textContent, 120);
            }
            if (head) {
                return squash(head.textContent, 120);
            }
            holder = holder.parentElement ? holder.parentElement.closest('section, article, form, .block, details') : null;
        }
        var h = document.querySelector('main h1');
        return h ? squash(h.textContent, 120) : '';
    }

    function describe(node) {
        var clone = node.cloneNode(false);
        return {
            selector: cssPath(node),
            tag: node.tagName.toLowerCase(),
            text: squash(node.innerText || node.textContent, 300),
            html: squash(clone.outerHTML, 400),
            section: sectionTitle(node),
            context: squash(node.parentElement ? node.parentElement.innerText : '', 300)
        };
    }

    // ---------- обмен с сервером
    function send(url, options) {
        options = options || {};
        options.headers = options.headers || {};
        options.credentials = 'same-origin';
        if (csrf && csrfHeader) {
            options.headers[csrfHeader] = csrf;
        }
        return fetch(url, options);
    }

    function load() {
        return send('/feedback/comments?path=' + encodeURIComponent(location.pathname)).then(function (r) {
            return r.ok ? r.json() : [];
        }).then(function (list) {
            comments = list;
            badge.textContent = String(list.length);
            badge.hidden = list.length === 0;
            drawPins();
            if (!panel.hidden) {
                drawPanel();
            }
        });
    }

    function post(payload) {
        return send('/feedback/comments', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
    }

    function basePayload() {
        return {
            pagePath: location.pathname,
            pageQuery: location.search,
            pageTitle: document.title,
            route: document.body.getAttribute('data-route') || '',
            view: document.body.getAttribute('data-view') || '',
            viewport: window.innerWidth + 'x' + window.innerHeight
        };
    }

    // ---------- метки на странице
    function drawPins() {
        layer.textContent = '';
        comments.forEach(function (c, index) {
            if (!c.onElement) {
                return;
            }
            var target = null;
            try {
                target = document.querySelector(c.selector);
            } catch (e) {
                target = null;
            }
            if (!target) {
                return;
            }
            var rect = target.getBoundingClientRect();
            if (rect.width === 0 && rect.height === 0) {
                return;
            }
            var pin = el('button', 'fb-pin', String(index + 1));
            pin.type = 'button';
            pin.style.left = (window.scrollX + rect.right - 10) + 'px';
            pin.style.top = (window.scrollY + rect.top - 10) + 'px';
            pin.addEventListener('click', function () {
                openPanel();
                var item = panel.querySelector('[data-id="' + c.id + '"]');
                if (item) {
                    item.scrollIntoView({block: 'nearest'});
                    item.classList.add('fb-flash');
                }
            });
            layer.appendChild(pin);
        });
    }

    // ---------- панель
    function openPanel() {
        panel.hidden = false;
        drawPanel();
    }

    function drawPanel() {
        panel.textContent = '';
        var head = el('div', 'fb-head');
        head.appendChild(el('strong', null, label('title')));
        var close = el('button', 'fb-x', '×');
        close.type = 'button';
        close.setAttribute('aria-label', label('close'));
        close.addEventListener('click', function () {
            panel.hidden = true;
        });
        head.appendChild(close);
        panel.appendChild(head);

        var actions = el('div', 'fb-actions');
        var pick = el('button', 'fb-btn primary', label('pick'));
        pick.type = 'button';
        pick.addEventListener('click', startPicking);
        var whole = el('button', 'fb-btn', label('page'));
        whole.type = 'button';
        whole.addEventListener('click', function () {
            openForm(null);
        });
        actions.appendChild(pick);
        actions.appendChild(whole);
        panel.appendChild(actions);

        var list = el('div', 'fb-list');
        if (comments.length === 0) {
            list.appendChild(el('p', 'fb-empty', label('empty')));
        }
        comments.forEach(function (c, index) {
            list.appendChild(commentNode(c, index + 1));
        });
        panel.appendChild(list);
    }

    function commentNode(c, number) {
        var item = el('div', 'fb-item');
        item.setAttribute('data-id', c.id);
        var top = el('div', 'fb-item-top');
        top.appendChild(el('span', 'fb-num', c.onElement ? String(number) : '#'));
        top.appendChild(el('strong', null, c.authorName));
        top.appendChild(el('span', 'fb-kind', label('kind-' + c.kind.toLowerCase())));
        top.appendChild(el('span', 'fb-status fb-' + c.status, label('status-' + c.status.toLowerCase())));
        item.appendChild(top);
        item.appendChild(el('div', 'fb-when', c.createdAt));
        if (c.onElement) {
            var found = false;
            try {
                found = !!document.querySelector(c.selector);
            } catch (e) {
                found = false;
            }
            var where = el('div', 'fb-where', (c.section ? c.section + ': ' : '') + (c.text ? '«' + c.text.slice(0, 60) + '»' : c.selector));
            if (!found) {
                where.appendChild(el('em', null, ' (' + label('notfound') + ')'));
            }
            item.appendChild(where);
        } else {
            item.appendChild(el('div', 'fb-where', label('whole')));
        }
        item.appendChild(el('p', 'fb-body', c.body));
        if (c.resolutionNote) {
            item.appendChild(el('p', 'fb-note', c.resolutionNote));
        }
        (c.replies || []).forEach(function (r) {
            var reply = el('div', 'fb-reply');
            reply.appendChild(el('strong', null, r.authorName + ': '));
            reply.appendChild(document.createTextNode(r.body));
            item.appendChild(reply);
        });
        var form = el('form', 'fb-reply-form');
        var input = el('input');
        input.type = 'text';
        input.maxLength = 4000;
        input.placeholder = label('reply-placeholder');
        var go = el('button', 'fb-btn', label('reply'));
        go.type = 'submit';
        form.appendChild(input);
        form.appendChild(go);
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            if (!input.value.trim()) {
                return;
            }
            var payload = basePayload();
            payload.body = input.value;
            payload.parentId = c.id;
            post(payload).then(function (r) {
                if (r.ok) {
                    load();
                } else {
                    window.alert(label('error'));
                }
            });
        });
        item.appendChild(form);
        var host = document.getElementById('hub-feedback');
        if (host && host.dataset.canManage === 'true') {
            var done = c.status === 'DONE';
            var resolve = el('button', 'fb-btn fb-resolve' + (done ? ' is-done' : ''), done ? label('reopen') : label('resolve'));
            resolve.type = 'button';
            resolve.addEventListener('click', function () {
                resolve.disabled = true;
                var headers = {'Content-Type': 'application/x-www-form-urlencoded'};
                if (csrf && csrfHeader) {
                    headers[csrfHeader] = csrf;
                }
                fetch('/feedback/comments/' + c.id + '/resolve', {method: 'POST', headers: headers, body: 'resolved=' + (!done)})
                    .then(function (r) {
                        if (r.ok) {
                            load();
                        } else {
                            resolve.disabled = false;
                            window.alert(label('error'));
                        }
                    });
            });
            item.appendChild(resolve);
        }
        if (c.status === 'DONE') {
            item.classList.add('fb-resolved');
        }
        return item;
    }

    // ---------- форма нового комментария
    function openForm(target) {
        var info = target ? describe(target) : null;
        var box = el('div', 'fb-form');
        box.appendChild(el('div', 'fb-form-title', info ? (info.section ? info.section + ': ' : '') + (info.text || info.selector).slice(0, 80) : label('whole')));
        var kind = el('select', 'fb-kind-select');
        KINDS.forEach(function (k) {
            var option = el('option', null, label('kind-' + k.toLowerCase()));
            option.value = k;
            kind.appendChild(option);
        });
        kind.value = 'IDEA';
        var text = el('textarea');
        text.rows = 4;
        text.maxLength = 4000;
        text.placeholder = label('placeholder');
        var row = el('div', 'fb-actions');
        var ok = el('button', 'fb-btn primary', label('send'));
        ok.type = 'button';
        var cancel = el('button', 'fb-btn', label('cancel'));
        cancel.type = 'button';
        row.appendChild(ok);
        row.appendChild(cancel);
        box.appendChild(kind);
        box.appendChild(text);
        box.appendChild(row);
        panel.hidden = false;
        panel.textContent = '';
        panel.appendChild(box);
        text.focus();
        cancel.addEventListener('click', function () {
            drawPanel();
        });
        ok.addEventListener('click', function () {
            if (!text.value.trim()) {
                text.focus();
                return;
            }
            var payload = basePayload();
            payload.kind = kind.value;
            payload.body = text.value;
            if (info) {
                payload.selector = info.selector;
                payload.tag = info.tag;
                payload.text = info.text;
                payload.html = info.html;
                payload.section = info.section;
                payload.context = info.context;
            }
            ok.disabled = true;
            post(payload).then(function (r) {
                if (r.ok) {
                    load();
                    drawPanel();
                } else {
                    ok.disabled = false;
                    window.alert(label('error'));
                }
            });
        });
    }

    // ---------- выбор элемента
    function inOurUi(node) {
        return !node || host.contains(node) || layer.contains(node) || (highlight && highlight.contains(node));
    }

    function stopPicking() {
        picking = false;
        document.documentElement.classList.remove('fb-picking');
        if (highlight) {
            highlight.remove();
            highlight = null;
        }
        hint.hidden = true;
    }

    var hint = el('div', 'fb-hint', label('hint'));
    hint.hidden = true;
    document.body.appendChild(hint);

    function startPicking() {
        picking = true;
        panel.hidden = true;
        hint.hidden = false;
        document.documentElement.classList.add('fb-picking');
        highlight = el('div', 'fb-highlight');
        document.body.appendChild(highlight);
    }

    document.addEventListener('mousemove', function (e) {
        if (!picking || !highlight) {
            return;
        }
        var target = e.target;
        if (inOurUi(target)) {
            return;
        }
        var rect = target.getBoundingClientRect();
        highlight.style.left = rect.left + 'px';
        highlight.style.top = rect.top + 'px';
        highlight.style.width = rect.width + 'px';
        highlight.style.height = rect.height + 'px';
    }, true);

    document.addEventListener('click', function (e) {
        if (!picking || inOurUi(e.target)) {
            return;
        }
        e.preventDefault();
        e.stopPropagation();
        var target = e.target;
        stopPicking();
        openForm(target);
    }, true);

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && picking) {
            stopPicking();
            openPanel();
        }
    });

    toggle.addEventListener('click', function () {
        if (panel.hidden) {
            openPanel();
        } else {
            panel.hidden = true;
        }
    });

    window.addEventListener('resize', drawPins);
    document.body.addEventListener('htmx:afterSettle', drawPins);
    load();
})();
