(function () {
    function linkSites() {
        var description = document.querySelector('#block-text p.pre');
        if (!description) { return; }
        var nodes = Array.prototype.slice.call(description.childNodes);
        var pattern = /https:\/\/(?:www\.)?(?:eltex\.ru|eltex-co\.com)\/[^\s<>"']*/gi;
        nodes.forEach(function (node) {
            if (node.nodeType !== Node.TEXT_NODE) { return; }
            var text = node.nodeValue;
            var match;
            var start = 0;
            var fragment = document.createDocumentFragment();
            pattern.lastIndex = 0;
            while ((match = pattern.exec(text)) !== null) {
                var value = match[0].replace(/[.,;!?]+$/, '');
                fragment.appendChild(document.createTextNode(text.slice(start, match.index)));
                var link = document.createElement('a');
                link.href = value;
                link.textContent = value;
                link.target = '_blank';
                link.rel = 'noopener noreferrer';
                fragment.appendChild(link);
                start = match.index + value.length;
            }
            if (start) {
                fragment.appendChild(document.createTextNode(text.slice(start)));
                node.replaceWith(fragment);
            }
        });
    }
    document.addEventListener('DOMContentLoaded', linkSites);
    document.body.addEventListener('htmx:afterSwap', linkSites);
})();
