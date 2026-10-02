/* DIH Client site Worker: the download counter. Only /dl/* and /api/* reach it (see wrangler.jsonc);
   every other request is a static file.

   GET /dl/<channel>/<minecraft>-<loader>   e.g. /dl/stable/26.2-fabric
     Redirects to that build's jar on GitHub, as listed in assets/release.js, and counts it once per
     visitor, file and day. The link always points at the channel's current release.
   GET /api/downloads
     { "total": n, "files": { "<jar>": n, ... } } */

const JSON_HEADERS = { 'Cache-Control': 'public, max-age=60', 'X-Content-Type-Options': 'nosniff' };
// link previews and crawlers follow links too; they are not downloads
const NOT_A_PERSON = /bot|crawl|spider|slurp|preview|embed|facebookexternalhit|whatsapp|telegram|headless|lighthouse/i;

export default {
  async fetch(request, env, ctx) {
    const { pathname } = new URL(request.url);
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      return new Response('Method not allowed', { status: 405, headers: { Allow: 'GET, HEAD' } });
    }
    if (pathname === '/api/downloads') return totals(env);
    const dl = /^\/dl\/([a-z0-9]+)\/([a-z0-9.]+-[a-z]+)\/?$/.exec(pathname);
    if (dl) return download(request, env, ctx, dl[1], dl[2]);
    return env.ASSETS.fetch(request);
  }
};

async function download(request, env, ctx, channel, build) {
  let R;
  try { R = await releaseData(env, request.url); } catch (e) {
    console.error('release.js unreadable', e);
    return redirect('https://github.com/ahigherdesire/dih-client/releases');
  }
  const channels = R.channels || {};
  const ch = Object.hasOwn(channels, channel) ? channels[channel] : null;
  const b = ch && Object.hasOwn(ch.builds, build) ? ch.builds[build] : null;
  if (!b) return env.ASSETS.fetch(request); // no such file: the site's 404 page

  if (request.method === 'GET' && isPerson(request)) {
    ctx.waitUntil(count(env, request, b.file).catch(e => console.error('count failed', b.file, e)));
  }
  // a build not yet ported to the channel's newest version names its own release
  return redirect(R.repo + '/releases/download/' + (b.tag || ch.tag) + '/' + b.file);
}

function redirect(url) {
  return new Response(null, { status: 302, headers: { Location: url, 'Cache-Control': 'no-store' } });
}

function isPerson(request) {
  const ua = request.headers.get('User-Agent') || '';
  const prefetch = /prefetch|prerender/i.test(request.headers.get('Sec-Purpose') || request.headers.get('Purpose') || '');
  return ua !== '' && !NOT_A_PERSON.test(ua) && !prefetch;
}

// assets/release.js is `window.DIH_RELEASE = { ...JSON... };`. It only changes with a deploy, and a deploy
// starts fresh isolates, so it is read once per isolate.
let release = null;
async function releaseData(env, base) {
  if (!release) {
    const res = await env.ASSETS.fetch(new URL('/assets/release.js', base));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const text = await res.text();
    const start = text.indexOf('{', text.indexOf('DIH_RELEASE'));
    release = JSON.parse(text.slice(start, text.lastIndexOf('}') + 1));
  }
  return release;
}

async function count(env, request, file) {
  const day = new Date().toISOString().slice(0, 10);
  const ip = request.headers.get('CF-Connecting-IP') || '';
  const key = await visitorKey(env, day + '|' + ip + '|' + file);
  const first = await env.DB.prepare('INSERT OR IGNORE INTO seen (key, day) VALUES (?, ?)').bind(key, day).run();
  if (!first.meta.changes) return;
  await env.DB.batch([
    env.DB.prepare('INSERT INTO downloads (file, count) VALUES (?, 1) ON CONFLICT (file) DO UPDATE SET count = count + 1').bind(file),
    env.DB.prepare('DELETE FROM seen WHERE day < ?').bind(day)
  ]);
}

// HMAC under the salt from the first migration, so the table never holds anything an IP can be read back from
let hmacKey = null;
async function visitorKey(env, data) {
  if (!hmacKey) {
    const salt = await env.DB.prepare("SELECT v FROM meta WHERE k = 'salt'").first('v');
    hmacKey = await crypto.subtle.importKey('raw', new TextEncoder().encode(salt), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  }
  const mac = new Uint8Array(await crypto.subtle.sign('HMAC', hmacKey, new TextEncoder().encode(data)));
  return Array.from(mac.subarray(0, 16), x => x.toString(16).padStart(2, '0')).join('');
}

async function totals(env) {
  try {
    const { results } = await env.DB.prepare('SELECT file, count FROM downloads ORDER BY file').all();
    const files = {};
    let total = 0;
    for (const r of results) { files[r.file] = r.count; total += r.count; }
    return Response.json({ total, files }, { headers: JSON_HEADERS });
  } catch (e) {
    console.error('totals failed', e);
    return Response.json({ error: 'unavailable' }, { status: 503, headers: { 'Cache-Control': 'no-store' } });
  }
}
