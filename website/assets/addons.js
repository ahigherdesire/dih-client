/* Addon marketplace (addons.html). The list comes from /api/addons (the Worker's copy of the reviewed registry),
   falling back to /addons.json. Listings are community data, so everything is set with textContent, links are
   only made for https URLs and images only load from the addon's GitHub repo. */
(function () {
  "use strict";

  var BUILTIN = [
    { id: "dih-coords", color: "#67e8f9", name: "Coords HUD", version: "1.0.0", author: "DIH", license: "GPL-3.0-or-later",
      summary: "XYZ, facing and Nether coords. Adds .coords",
      description: "Shows your position, the direction you face and the matching Overworld or Nether coordinates on the HUD. .coords prints them in chat; .coords copy puts x y z on the clipboard.",
      categories: ["hud"] },
    { id: "dih-deathpoint", color: "#ef4444", name: "Death Point", version: "1.0.0", author: "DIH", license: "GPL-3.0-or-later",
      summary: "Shows where you died. Adds .deathpoint",
      description: "Remembers where you last died this session and shows its coordinates and distance on the HUD. .deathpoint prints it; .deathpoint clear forgets it.",
      categories: ["hud", "utility"] },
    { id: "dih-sessionstats", color: "#8ba6ff", name: "Session Stats", version: "1.0.0", author: "DIH", license: "GPL-3.0-or-later",
      summary: "Time played, blocks walked and FPS",
      description: "A HUD line with your time in the current world, how many blocks you have walked and your frame rate.",
      categories: ["hud"] }
  ];
  BUILTIN.forEach(function (a) { a.builtin = true; a.files = []; });

  var ACCENTS = ["#67e8f9", "#8ba6ff", "#a855f7", "#4ade80", "#fbbf24", "#f472b6", "#ef4444"];
  var $ = function (id) { return document.getElementById(id); };
  var community = [];
  var query = "";
  var category = "";

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function str(v, max) {
    if (typeof v !== "string") return "";
    max = max || 400;
    return v.length > max ? v.slice(0, max - 1).replace(/\s+\S*$/, "") + "…" : v;
  }

  function https(v) { return typeof v === "string" && v.indexOf("https://") === 0 ? v : null; }

  // card images must live in the addon's own GitHub repo (the registry enforces the same rule)
  function imageUrl(a) {
    var u = https(a.image);
    if (!u || u.indexOf("..") !== -1 || !/\.(png|jpe?g)$/i.test(u)) return null;
    return /^https:\/\/(raw\.githubusercontent\.com|github\.com)\//.test(u) ? u : null;
  }

  // same colour the in-game browser picks: Java's String.hashCode, golden-ratio mix, one of seven accents
  function accent(id) {
    var h = 0;
    for (var i = 0; i < id.length; i++) h = (Math.imul(31, h) + id.charCodeAt(i)) | 0;
    h = Math.imul(h, 0x9e3779b9 | 0);
    var m = (h ^ (h >>> 15)) % ACCENTS.length;
    return ACCENTS[m < 0 ? m + ACCENTS.length : m];
  }

  // built-in addons keep the colour the client gives them; listings get one from their id
  function tint(a) { return a.builtin && a.color ? a.color : accent(a.id); }

  function initials(name) {
    var words = str(name, 80).split(/[^A-Za-z0-9]+/).filter(Boolean);
    return (words.slice(0, 2).map(function (w) { return w[0].toUpperCase(); }).join("")) || "?";
  }

  function size(bytes) {
    if (typeof bytes !== "number") return "";
    return bytes >= 1048576 ? (bytes / 1048576).toFixed(1) + " MB" : Math.max(1, Math.round(bytes / 1024)) + " KB";
  }

  function files(a) { return Array.isArray(a.files) ? a.files.filter(function (f) { return f && typeof f === "object"; }) : []; }

  function versions(a) {
    return files(a).map(function (f) { return str(f.minecraft, 12); }).filter(Boolean).join(", ");
  }

  function cats(a) { return Array.isArray(a.categories) ? a.categories.filter(function (c) { return typeof c === "string"; }) : []; }

  function media(a, box) {
    var url = imageUrl(a);
    var banner = el("div", "ad-banner");
    banner.appendChild(el("span", null, initials(a.name)));
    if (!url) { box.appendChild(banner); return; }
    var img = el("img");
    img.alt = "";
    img.loading = "lazy";
    img.decoding = "async";
    img.referrerPolicy = "no-referrer";
    img.onerror = function () { img.remove(); box.insertBefore(banner, box.firstChild); };
    img.src = url;
    box.appendChild(img);
  }

  function card(a) {
    var c = el("a", "ad-card");
    c.href = "#" + encodeURIComponent(a.id);
    c.style.setProperty("--accent", tint(a));
    c.setAttribute("aria-label", str(a.name, 40) + ": " + str(a.summary, 70));

    var m = el("div", "ad-media");
    media(a, m);
    m.appendChild(el("span", "ad-pill" + (a.builtin ? " builtin" : ""), a.builtin ? "Built in" : "MC " + versions(a)));
    c.appendChild(m);

    var b = el("div", "ad-body");
    var t = el("div", "ad-title");
    t.appendChild(el("h3", null, str(a.name, 40)));
    t.appendChild(el("span", "ad-ver", "v" + str(a.version, 24)));
    b.appendChild(t);
    b.appendChild(el("p", "ad-sum", str(a.summary, 70) || str(a.description, 140)));
    var meta = str(a.author, 60) + (cats(a).length ? "  ·  " + cats(a).join(", ") : "");
    b.appendChild(el("p", "ad-meta", meta));
    c.appendChild(b);
    return c;
  }

  function matches(a) {
    if (category && cats(a).indexOf(category) === -1) return false;
    if (!query) return true;
    var hay = [a.name, a.author, a.summary, a.description, cats(a).join(" ")].map(function (v) { return str(v, 400); }).join(" ").toLowerCase();
    return hay.indexOf(query) !== -1;
  }

  function render() {
    var grid = $("community");
    grid.textContent = "";
    var shown = community.filter(matches);
    shown.forEach(function (a) { grid.appendChild(card(a)); });
    if (!shown.length) {
      var empty = el("div", "ad-empty");
      if (community.length) {
        empty.appendChild(el("strong", null, "No community addons match"));
        empty.appendChild(el("span", null, "Try another word or category."));
      } else {
        empty.appendChild(el("strong", null, "No community addons yet"));
        empty.appendChild(el("span", null, "Made one? Get it listed and every DIH player can install it in two clicks."));
        var go = el("a", null, "Publish your addon →");
        go.href = "#publish";
        empty.appendChild(go);
      }
      grid.appendChild(empty);
    }
    var built = $("builtin");
    built.textContent = "";
    BUILTIN.filter(matches).forEach(function (a) { built.appendChild(card(a)); });
    $("builtin").closest("section").hidden = !built.children.length;
  }

  function renderFilters() {
    var all = {};
    community.concat(BUILTIN).forEach(function (a) { cats(a).forEach(function (c) { all[c] = true; }); });
    var box = $("filters");
    box.textContent = "";
    [""].concat(Object.keys(all).sort()).forEach(function (c) {
      var b = el("button", "ad-filter", c || "all");
      b.type = "button";
      b.setAttribute("aria-pressed", String(c === category));
      b.addEventListener("click", function () {
        category = c;
        Array.prototype.forEach.call(box.children, function (x) { x.setAttribute("aria-pressed", String(x === b)); });
        render();
      });
      box.appendChild(b);
    });
  }

  function skeletons() {
    var grid = $("community");
    for (var i = 0; i < 3; i++) {
      var c = el("div", "ad-card ad-skel");
      c.setAttribute("aria-hidden", "true");
      c.appendChild(el("div", "ad-media"));
      var b = el("div", "ad-body");
      ["60%", "100%", "80%"].forEach(function (w) { var line = el("i"); line.style.width = w; b.appendChild(line); });
      c.appendChild(b);
      grid.appendChild(c);
    }
  }

  // ---- dialog

  var dialog = $("dialog");

  function info(dl, label, value) {
    if (!value) return;
    dl.appendChild(el("dt", null, label));
    dl.appendChild(el("dd", null, value));
  }

  function find(id) {
    return community.concat(BUILTIN).filter(function (a) { return a.id === id; })[0] || null;
  }

  function open(a) {
    dialog.style.setProperty("--accent", tint(a));
    var m = $("d-media");
    m.textContent = "";
    media(a, m);
    $("d-badge").textContent = a.builtin ? "Built into DIH" : "Community addon";
    $("d-name").textContent = str(a.name, 40);
    $("d-by").textContent = "by " + str(a.author, 60);
    var c = $("d-cats");
    c.textContent = "";
    cats(a).forEach(function (x) { c.appendChild(el("span", "ad-cat", x)); });
    $("d-summary").textContent = str(a.summary, 70);
    $("d-desc").textContent = str(a.description, 300) === str(a.summary, 70) ? "" : str(a.description, 300);

    var dl = $("d-info");
    dl.textContent = "";
    info(dl, "Version", "v" + str(a.version, 24));
    info(dl, "Minecraft", a.builtin ? "26.2, 26.3 (DIH 5.1 beta 7+)" : versions(a));
    info(dl, "Licence", str(a.license, 40));
    if (!a.builtin) {
      info(dl, "Addon API", typeof a.apiVersion === "number" ? "v" + a.apiVersion : "");
      info(dl, "Download", files(a).map(function (f) { return size(f.size); }).filter(Boolean).join(" / "));
    }

    var act = $("d-actions");
    act.textContent = "";
    if (a.builtin) {
      act.appendChild(tip("In DIH open <b>Addons</b>, select it, click <b>Enable Restart</b>, then restart. No download needed."));
    } else {
      files(a).forEach(function (f) {
        var jar = https(f.jar);
        if (!jar) return;
        var b = el("a", "ad-btn");
        b.href = jar;
        b.rel = "noopener";
        b.insertAdjacentHTML("afterbegin", '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 2.5v8m0 0L4.8 7.3M8 10.5l3.2-3.2M3 13.5h10"/></svg>');
        b.appendChild(document.createTextNode("Download for " + str(f.minecraft, 12) + " "));
        b.appendChild(el("small", null, size(f.size)));
        act.appendChild(b);
      });
      var src = https(a.source);
      if (src) {
        var s = el("a", "ad-btn ghost", "Source code");
        s.href = src;
        s.rel = "noopener";
        act.appendChild(s);
      }
      act.appendChild(tip("Easier: in DIH open <b>Addons</b>, then <b>Browse Online</b> and click Install. It checks the SHA-256 for you."));
    }

    var sums = $("d-sums");
    var list = $("d-sumlist");
    list.textContent = "";
    files(a).forEach(function (f) {
      var row = el("div", "ad-sum-row");
      row.appendChild(el("span", null, str(f.minecraft, 12)));
      row.appendChild(el("code", null, str(f.sha256, 64)));
      list.appendChild(row);
    });
    sums.hidden = !list.children.length;
    sums.open = false;

    if (!dialog.open) dialog.showModal();
    dialog.scrollTop = 0;
  }

  // the tip text is ours, not listing data, so its <b> tags are safe
  function tip(html) {
    var p = el("p", "ad-tip");
    p.innerHTML = html;
    p.style.flexBasis = "100%";
    return p;
  }

  function fromHash() {
    var id = decodeURIComponent(location.hash.slice(1));
    var a = id && find(id);
    if (a) open(a);
    else if (dialog.open && !id) dialog.close();
  }

  function close() {
    if (dialog.open) dialog.close();
  }

  dialog.addEventListener("close", function () {
    if (location.hash && find(decodeURIComponent(location.hash.slice(1)))) {
      history.replaceState(null, "", location.pathname + location.search);
    }
  });
  dialog.addEventListener("click", function (e) { if (e.target === dialog) close(); });
  $("d-close").addEventListener("click", close);
  window.addEventListener("hashchange", fromHash);

  $("q").addEventListener("input", function (e) {
    query = e.target.value.trim().toLowerCase();
    render();
  });

  // ---- load

  function load(url) {
    return fetch(url, { cache: "no-cache" }).then(function (r) {
      if (!r.ok) throw new Error(String(r.status));
      return r.json();
    }).then(function (data) {
      if (!data || !Array.isArray(data.addons)) throw new Error("bad registry");
      return data.addons.filter(function (a) {
        return a && typeof a.id === "string" && /^[a-z0-9][a-z0-9_-]{1,39}$/.test(a.id) && !/^dih([-_]|$)/.test(a.id);
      });
    });
  }

  skeletons();
  BUILTIN.forEach(function (a) { $("builtin").appendChild(card(a)); });
  var live = true;
  load("/api/addons")
    .catch(function () { live = false; return load("/addons.json"); })
    .then(function (list) {
      community = list.sort(function (x, y) { return str(x.name).localeCompare(str(y.name)); });
      $("status").textContent = live ? "Live from the reviewed registry" : "Showing the saved list";
    })
    .catch(function () {
      $("status").textContent = "Could not load the list. Try again in a minute.";
    })
    .then(function () {
      $("stat-community").textContent = String(community.length);
      renderFilters();
      render();
      fromHash();
    });
})();
