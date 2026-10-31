/*
 * Rendering logic. Reads the JSON lines in #report-data (a "meta" line, then "call" lines) and builds the DOM
 * inside #app; it sets no styles of its own, so a stylesheet fully controls the look. The class names are the contract with report.css:
 *
 *   .summary > .stat (> .label, .value) | .toolbar > input.filter, select.thread | .error
 *   .section > h2 | table.slowest (th.num, td.num, td.method)
 *   .tree > details.call[.slow] > summary (.method, .duration, .bar > .fill[style=width:N%]) > .children
 *   .muted
 */
(function () {
  'use strict';
  var lines = document.getElementById('report-data').textContent.split('\n').filter(function (l) { return l.trim(); });
  var parsed = lines.map(function (l) { return JSON.parse(l); });
  var data = parsed.filter(function (o) { return o.type === 'meta'; })[0] || { thresholdMs: 0, dropped: 0 };
  var records = parsed.filter(function (o) { return o.type === 'call'; });
  var app = document.getElementById('app');

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }

  function fmt(ms) {
    return ms >= 1000 ? (ms / 1000).toFixed(2) + ' s' : ms + ' ms';
  }

  function stat(label, value) {
    var s = el('div', 'stat');
    s.appendChild(el('div', 'label', label));
    s.appendChild(el('div', 'value', value));
    return s;
  }

  // records arrive parents-first; per thread, depth tells where each call hangs in the tree.
  function buildTrees(list) {
    var roots = {}, stacks = {};
    list.forEach(function (r) {
      var node = { r: r, children: [] };
      var stack = stacks[r.thread] || (stacks[r.thread] = []);
      stack.length = Math.min(stack.length, r.depth);
      var parent = stack[r.depth - 1];
      // A missing parent (dropped or below threshold) lifts the call to the nearest recorded ancestor.
      for (var d = r.depth - 1; !parent && d >= 0; d--) parent = stack[d];
      if (parent) parent.children.push(node); else (roots[r.thread] || (roots[r.thread] = [])).push(node);
      stack[r.depth] = node;
    });
    return roots;
  }

  function callNode(node, max) {
    var d = el('details', 'call' + (node.r.durationMs >= max * 0.5 ? ' slow' : ''));
    d.open = true;
    var s = el('summary');
    s.appendChild(el('span', 'method', node.r.method));
    s.appendChild(el('span', 'duration', fmt(node.r.durationMs)));
    var bar = el('span', 'bar'), fill = el('span', 'fill');
    fill.style.width = Math.max(1, node.r.durationMs / max * 100) + '%';
    bar.appendChild(fill);
    s.appendChild(bar);
    d.appendChild(s);
    if (node.children.length) {
      var kids = el('div', 'children');
      node.children.forEach(function (c) { kids.appendChild(callNode(c, max)); });
      d.appendChild(kids);
    }
    return d;
  }

  function slowestTable(list, n) {
    var totals = {};
    list.forEach(function (r) {
      var t = totals[r.method] || (totals[r.method] = { method: r.method, calls: 0, total: 0, max: 0 });
      t.calls++; t.total += r.durationMs; t.max = Math.max(t.max, r.durationMs);
    });
    var rows = Object.keys(totals).map(function (k) { return totals[k]; })
      .sort(function (a, b) { return b.total - a.total; }).slice(0, n);
    var table = el('table', 'slowest'), head = el('tr');
    [['Method', ''], ['Calls', 'num'], ['Total', 'num'], ['Max', 'num']].forEach(function (h) {
      head.appendChild(el('th', h[1], h[0]));
    });
    table.appendChild(head);
    rows.forEach(function (t) {
      var tr = el('tr');
      tr.appendChild(el('td', 'method', t.method));
      tr.appendChild(el('td', 'num', t.calls));
      tr.appendChild(el('td', 'num', fmt(t.total)));
      tr.appendChild(el('td', 'num', fmt(t.max)));
      table.appendChild(tr);
    });
    return table;
  }

  function render() {
    app.textContent = '';
    var summary = el('div', 'summary');
    var slowest = records.reduce(function (m, r) { return Math.max(m, r.durationMs); }, 0);
    summary.appendChild(stat('Slow calls', records.length));
    summary.appendChild(stat('Threshold', fmt(data.thresholdMs)));
    summary.appendChild(stat('Slowest call', fmt(slowest)));
    summary.appendChild(stat('Dropped', data.dropped));
    app.appendChild(summary);

    if (!records.length) {
      app.appendChild(el('p', 'muted', 'No calls exceeded the threshold.'));
      return;
    }

    var toolbar = el('div', 'toolbar');
    var filter = el('input', 'filter');
    filter.type = 'search';
    filter.placeholder = 'Filter by method';
    var threads = el('select', 'thread');
    threads.appendChild(new Option('All threads', ''));
    Object.keys(buildTrees(records)).sort().forEach(function (t) { threads.appendChild(new Option(t, t)); });
    toolbar.appendChild(filter);
    toolbar.appendChild(threads);
    app.appendChild(toolbar);

    var slowSection = el('div', 'section'), treeSection = el('div', 'section');
    app.appendChild(slowSection);
    app.appendChild(treeSection);

    function refresh() {
      var q = filter.value.toLowerCase();
      var matching = records.filter(function (r) {
        return (!threads.value || r.thread === threads.value) && r.method.toLowerCase().indexOf(q) >= 0;
      });
      slowSection.textContent = '';
      slowSection.appendChild(el('h2', '', 'Slowest methods'));
      slowSection.appendChild(slowestTable(matching, 10));
      treeSection.textContent = '';
      treeSection.appendChild(el('h2', '', 'Call tree'));
      var trees = buildTrees(matching);
      Object.keys(trees).sort().forEach(function (t) {
        treeSection.appendChild(el('h3', 'thread-name', t));
        var tree = el('div', 'tree');
        trees[t].forEach(function (n) { tree.appendChild(callNode(n, slowest)); });
        treeSection.appendChild(tree);
      });
      if (!matching.length) treeSection.appendChild(el('p', 'muted', 'No calls match.'));
    }
    filter.addEventListener('input', refresh);
    threads.addEventListener('change', refresh);
    refresh();
  }

  render();
})();
