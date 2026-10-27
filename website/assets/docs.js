// DIH Client: /docs search, contents highlighting, copy buttons and link arrival.
(function () {
  'use strict';

  var side = document.getElementById('side');
  var input = document.getElementById('q');
  var hits = document.getElementById('hits');
  var none = document.getElementById('hits-none');
  var toc = document.getElementById('toc');
  var toggle = document.getElementById('toc-toggle');
  var narrow = window.matchMedia('(max-width: 900px)');

  // ------------------------------------------------------------ index
  var entries = Array.prototype.map.call(document.querySelectorAll('.cmd'), function (el) {
    var name = el.getAttribute('data-name');
    var aliases = (el.getAttribute('data-aliases') || '').split(' ').filter(Boolean);
    var sum = el.querySelector('.sum');
    return {
      id: el.id,
      name: name,
      bare: name.slice(1).toLowerCase(),
      aliases: aliases,
      bareAliases: aliases.map(function (a) { return a.slice(1).toLowerCase(); }),
      beta: el.hasAttribute('data-beta'),
      summary: sum ? sum.textContent : '',
      summaryLow: sum ? sum.textContent.toLowerCase() : '',
      body: el.textContent.toLowerCase()
    };
  });

  function score(e, q, bare) {
    var hasPrefix = q[0] === '.' || q[0] === '#';
    if (hasPrefix && q[0] !== e.name[0]) return 0;
    var n = e.bare, s = 0;
    if (n === bare) s = 100;
    else if (n.indexOf(bare) === 0) s = 80;
    else if (e.bareAliases.indexOf(bare) !== -1) s = 75;
    else if (e.bareAliases.some(function (a) { return a.indexOf(bare) === 0; })) s = 60;
    else if (n.indexOf(bare) !== -1) s = 50;
    else if (e.summaryLow.indexOf(q) !== -1) s = 30;
    else if (q.split(/\s+/).every(function (w) { return e.body.indexOf(w) !== -1; })) s = 10;
    return s;
  }

  function search(raw) {
    var q = raw.trim().toLowerCase();
    if (!q) return [];
    var bare = q.replace(/^[.#]/, '');
    return entries
      .map(function (e) { return { e: e, s: score(e, q, bare) }; })
      .filter(function (r) { return r.s > 0; })
      .sort(function (a, b) { return b.s - a.s || a.e.name.length - b.e.name.length; })
      .slice(0, 30)
      .map(function (r) { return r.e; });
  }

  function esc(t) {
    return t.replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; });
  }

  function mark(text, q) {
    var i = q ? text.toLowerCase().indexOf(q) : -1;
    if (i < 0) return esc(text);
    return esc(text.slice(0, i)) + '<mark>' + esc(text.slice(i, i + q.length)) + '</mark>' + esc(text.slice(i + q.length));
  }

  // highlight in the part after the prefix so `.` and `#` never split the match
  function markName(name, bare) { return esc(name[0]) + mark(name.slice(1), bare); }

  // ------------------------------------------------------------ results
  var results = [], active = -1;

  function render() {
    var raw = input.value;
    var q = raw.trim().toLowerCase();
    var bare = q.replace(/^[.#]/, '');
    results = search(raw);
    active = results.length ? 0 : -1;
    var searching = q.length > 0;
    side.classList.toggle('searching', searching);
    toc.hidden = searching;
    hits.hidden = !searching || !results.length;
    none.hidden = !searching || results.length > 0;
    input.setAttribute('aria-expanded', String(searching && results.length > 0));
    hits.innerHTML = results.map(function (e, i) {
      var also = (e.beta ? '<span class="beta-tag">beta</span>' : '')
        + (e.aliases.length ? '<small>' + esc(e.aliases.join(' ')) + '</small>' : '');
      return '<li role="presentation"><a role="option" id="hit-' + i + '" href="#' + e.id + '"'
        + (i === active ? ' aria-selected="true"' : '') + '>'
        + '<span class="hit-name"><span>' + markName(e.name, bare) + '</span>' + also + '</span>'
        + '<span class="hit-sum">' + mark(e.summary, q) + '</span></a></li>';
    }).join('');
    syncActive();
  }

  function syncActive() {
    var links = hits.querySelectorAll('a');
    Array.prototype.forEach.call(links, function (a, i) {
      if (i === active) a.setAttribute('aria-selected', 'true');
      else a.removeAttribute('aria-selected');
    });
    if (active >= 0 && links[active]) {
      input.setAttribute('aria-activedescendant', 'hit-' + active);
      links[active].scrollIntoView({ block: 'nearest' });
    } else {
      input.removeAttribute('aria-activedescendant');
    }
  }

  function go(id) {
    var el = document.getElementById(id);
    if (!el) return;
    if (location.hash !== '#' + id) history.pushState(null, '', '#' + id);
    el.scrollIntoView({ block: 'start' });
    arrive(el);
    if (narrow.matches) {
      input.value = '';
      render();
      setOpen(false);
      input.blur();
    }
  }

  input.addEventListener('input', render);
  input.addEventListener('keydown', function (ev) {
    if (ev.key === 'ArrowDown' && results.length) {
      ev.preventDefault();
      active = (active + 1) % results.length;
      syncActive();
    } else if (ev.key === 'ArrowUp' && results.length) {
      ev.preventDefault();
      active = (active - 1 + results.length) % results.length;
      syncActive();
    } else if (ev.key === 'Enter' && active >= 0) {
      ev.preventDefault();
      go(results[active].id);
    } else if (ev.key === 'Escape') {
      input.value = '';
      render();
      input.blur();
    }
  });
  hits.addEventListener('click', function (ev) {
    var a = ev.target.closest('a');
    if (!a) return;
    ev.preventDefault();
    go(a.getAttribute('href').slice(1));
  });

  document.addEventListener('keydown', function (ev) {
    var typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName) || document.activeElement.isContentEditable;
    if ((ev.key === '/' && !typing) || ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 'k')) {
      ev.preventDefault();
      input.focus();
      input.select();
    }
  });

  // ------------------------------------------------------------ narrow-screen contents
  function setOpen(open) {
    side.classList.toggle('open', open);
    toggle.setAttribute('aria-expanded', String(open));
  }
  toggle.addEventListener('click', function () { setOpen(!side.classList.contains('open')); });
  toc.addEventListener('click', function (ev) {
    var a = ev.target.closest('a[href^="#"]');
    if (!a) return;
    ev.preventDefault();
    go(a.getAttribute('href').slice(1));
  });

  // ------------------------------------------------------------ arrival highlight
  function arrive(el) {
    if (!el.classList.contains('cmd')) return;
    el.classList.remove('arrive');
    void el.offsetWidth;
    el.classList.add('arrive');
  }
  document.querySelector('.doc').addEventListener('click', function (ev) {
    var a = ev.target.closest('a[href^="#"]');
    if (!a) return;
    var id = a.getAttribute('href').slice(1);
    var el = document.getElementById(id);
    if (el) setTimeout(function () { arrive(el); }, 0);
  });
  window.addEventListener('popstate', function () {
    var el = location.hash && document.getElementById(location.hash.slice(1));
    if (el) arrive(el);
  });
  if (location.hash) {
    var first = document.getElementById(location.hash.slice(1));
    if (first) arrive(first);
  }

  // ------------------------------------------------------------ contents follows the page
  var tocLinks = {};
  Array.prototype.forEach.call(toc.querySelectorAll('a[href^="#"]'), function (a) {
    tocLinks[a.getAttribute('href').slice(1)] = a;
  });
  var current = null;
  var watched = document.querySelectorAll('.cmd, .grp');
  var observer = new IntersectionObserver(function (items) {
    items.forEach(function (it) {
      if (!it.isIntersecting) return;
      var id = it.target.id;
      if (!tocLinks[id] || it.target === current) return;
      if (current && tocLinks[current.id]) tocLinks[current.id].classList.remove('on');
      current = it.target;
      tocLinks[id].classList.add('on');
      if (!narrow.matches && !side.classList.contains('searching')) {
        var link = tocLinks[id];
        var top = link.offsetTop, h = side.clientHeight;
        if (top < side.scrollTop + 80 || top > side.scrollTop + h - 80) side.scrollTop = top - h / 2;
      }
    });
  }, { rootMargin: '-64px 0px -70% 0px' });
  Array.prototype.forEach.call(watched, function (el) { observer.observe(el); });

  // ------------------------------------------------------------ copy buttons
  var copyIcon = '<svg viewBox="0 0 16 16" aria-hidden="true"><rect x="5.5" y="5.5" width="8" height="8" rx="1.5"/><path d="M10.5 5.5v-2A1.5 1.5 0 0 0 9 2H3.5A1.5 1.5 0 0 0 2 3.5V9A1.5 1.5 0 0 0 3.5 10.5h2"/></svg>';
  var doneIcon = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="m3.5 8.5 3 3 6-7"/></svg>';
  Array.prototype.forEach.call(document.querySelectorAll('.doc pre'), function (pre) {
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'copy';
    btn.setAttribute('aria-label', 'Copy');
    btn.innerHTML = copyIcon;
    btn.addEventListener('click', function () {
      var text = pre.querySelector('code').textContent;
      var done = function () {
        btn.classList.add('done');
        btn.innerHTML = doneIcon;
        btn.setAttribute('aria-label', 'Copied');
        setTimeout(function () {
          btn.classList.remove('done');
          btn.innerHTML = copyIcon;
          btn.setAttribute('aria-label', 'Copy');
        }, 1400);
      };
      if (navigator.clipboard) navigator.clipboard.writeText(text).then(done, function () {});
    });
    pre.appendChild(btn);
  });
})();
