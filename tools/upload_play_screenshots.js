// Replaces the Google Play phone screenshots of every Infiltrate store listing, in one Play "edit".
// Adapted from SaySplit's upload-play.js. Plain Node 18+, no npm packages.
//
// The screenshot folder holds <lang>/google_play_1920x1080/1.png ... N.png, where <lang> is the
// game's language code (en, fr, pt-BR, zh-TW, ...). Each existing Play listing is matched to a folder
// by its exact code, then by its base language (es-419 and es-ES both take es/). The feature graphic,
// tablet screenshots and listing texts are left alone.
//
//   node tools/upload_play_screenshots.js --key <service-account.json> --dir <folder>             dry run
//   node tools/upload_play_screenshots.js --key ... --dir ... --commit                              upload, send for review
//   node tools/upload_play_screenshots.js --key ... --dir ... --commit --no-review                  upload, don't send
//   options: --only en-US,fr-FR          just those listings
//            --new-listing fa=<file.txt> create that listing from the file if Play has none yet
//            --delete-listing fa         delete that listing (e.g. a duplicate) in the same edit
//                                        (line 1 title, line 2 short description, rest full description)
//
// Without --commit everything is staged in an edit that is then deleted, so Google still validates
// every upload. With --commit the edit is committed and sent for review; add --no-review to save it
// in Publishing overview unsent instead (Play only allows that while managed publishing is on).
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PKG = 'com.infiltrate.shadowheist';
const API = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' + PKG;
const UPLOAD = 'https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/' + PKG;
const SUBDIR = 'google_play_1920x1080';

const args = process.argv.slice(2);
const opt = f => { const i = args.indexOf(f); return i >= 0 ? args[i + 1] : null; };
const COMMIT = args.includes('--commit');
const KEY = opt('--key');
const DIR = opt('--dir');
const ONLY = opt('--only') ? opt('--only').split(',').map(s => s.trim()) : null;
const DELETE = opt('--delete-listing');
const NEW = opt('--new-listing') ? opt('--new-listing').split('=') : null;  // [language, file]
if (!KEY || !DIR) { console.error('Usage: --key <service-account.json> --dir <screenshots folder> [--commit]'); process.exit(2); }

function pngSize(file) {
  const b = fs.readFileSync(file);
  if (b.toString('latin1', 1, 4) !== 'PNG') throw new Error(file + ': not a PNG');
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20), bytes: b.length };
}

// Game language folders -> sorted PNG paths, checked against Play's phone screenshot limits.
function readFolders() {
  const folders = {}, problems = [];
  for (const lang of fs.readdirSync(DIR)) {
    const d = path.join(DIR, lang, SUBDIR);
    if (!fs.existsSync(d)) continue;
    const files = fs.readdirSync(d).filter(f => /\.png$/i.test(f))
      .sort((a, b) => parseInt(a) - parseInt(b) || a.localeCompare(b)).map(f => path.join(d, f));
    if (files.length < 2 || files.length > 8) problems.push(`${lang}: ${files.length} screenshots, Play takes 2-8`);
    for (const f of files) {
      const { w, h, bytes } = pngSize(f);
      if (Math.min(w, h) < 320 || Math.max(w, h) > 3840) problems.push(`${f}: ${w}x${h}, sides must be 320-3840 px`);
      if (Math.max(w, h) / Math.min(w, h) > 2) problems.push(`${f}: ${w}x${h}, ratio over 2:1`);
      if (bytes > 8 * 1024 * 1024) problems.push(`${f}: over 8 MB`);
    }
    folders[lang] = files;
  }
  return { folders, problems };
}

function folderFor(listing, folders) {
  if (folders[listing]) return listing;
  if (listing === 'zh-TW' || listing === 'zh-HK') return folders['zh-TW'] ? 'zh-TW' : null;
  const base = listing.split('-')[0];
  if (base === 'pt') return folders['pt-BR'] ? 'pt-BR' : null;
  if (base === 'zh') return null;  // simplified Chinese has no folder
  return folders[base] ? base : null;
}

function readListing(language, file) {
  const [title, shortDescription, ...rest] = fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n').split('\n');
  const fullDescription = rest.join('\n').trim();
  const t = { language, title: title.trim(), shortDescription: shortDescription.trim(), fullDescription };
  if ([...t.title].length > 30 || [...t.shortDescription].length > 80 || [...fullDescription].length > 4000) {
    throw new Error(file + ': over Play\'s 30/80/4000 character limits');
  }
  return t;
}

async function token(key) {
  const now = Math.floor(Date.now() / 1000);
  const b64 = o => Buffer.from(JSON.stringify(o)).toString('base64url');
  const unsigned = b64({ alg: 'RS256', typ: 'JWT' }) + '.' + b64({
    iss: key.client_email, scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600,
  });
  const sig = crypto.createSign('RSA-SHA256').update(unsigned).sign(key.private_key).toString('base64url');
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: unsigned + '.' + sig }),
  });
  const j = await res.json();
  if (!res.ok) throw new Error('Service account sign-in failed: ' + (j.error_description || j.error || res.status));
  return j.access_token;
}

async function main() {
  const { folders, problems } = readFolders();
  if (problems.length) { console.error('Not uploading, fix these first:\n  ' + problems.join('\n  ')); process.exit(1); }
  console.log(`Local: ${Object.keys(folders).length} language folders, all within Play's screenshot limits.`);
  const created = NEW ? readListing(NEW[0], NEW[1]) : null;

  const key = JSON.parse(fs.readFileSync(KEY, 'utf8'));
  const tok = await token(key);
  const call = async (method, url, body, type) => {
    let res;
    for (let attempt = 1; ; attempt++) {
      try {
        res = await fetch(url, { method, headers: { Authorization: 'Bearer ' + tok, ...(type ? { 'Content-Type': type } : {}) }, body });
        if ((res.status === 429 || res.status >= 500) && attempt < 6) throw new Error('HTTP ' + res.status);
        break;
      } catch (e) {
        if (attempt >= 6) throw new Error(`${method} ${url.replace(/^https:\/\/[^/]+/, '')} failed after ${attempt} tries: ${e.message}`);
        const wait = 2000 * 2 ** (attempt - 1);
        console.log(`    (${e.message}; retrying in ${wait / 1000}s)`);
        await new Promise(r => setTimeout(r, wait));
      }
    }
    const txt = await res.text();
    if (!res.ok) throw new Error(`${method} ${url.replace(/^https:\/\/[^/]+/, '')} -> ${res.status} ${txt}`);
    return txt ? JSON.parse(txt) : {};
  };

  const edit = await call('POST', API + '/edits', '{}', 'application/json');
  const E = API + '/edits/' + edit.id, U = UPLOAD + '/edits/' + edit.id;
  console.log(`Signed in as ${key.client_email}; edit ${edit.id}${COMMIT ? '' : ' (dry run)'}\n`);
  try {
    const listings = ((await call('GET', E + '/listings')).listings || []).map(l => l.language);
    console.log('Play listings now: ' + listings.join(', '));
    let targets = listings;
    if (created && !listings.includes(created.language)) {
      await call('PUT', `${E}/listings/${created.language}`, JSON.stringify(created), 'application/json');
      console.log(`  ${created.language}: listing created from ${path.basename(NEW[1])}`);
      targets = [...targets, created.language];
    }
    if (DELETE) {
      if (!listings.includes(DELETE)) throw new Error(`No ${DELETE} listing to delete`);
      await call('DELETE', `${E}/listings/${DELETE}`);
      console.log(`  ${DELETE}: listing deleted`);
      targets = targets.filter(l => l !== DELETE);
    }
    if (ONLY) targets = targets.filter(l => ONLY.includes(l));

    // Listings upload in parallel (each listing's images stay in order) to keep the edit open briefly:
    // Play refuses to commit an edit if anything else changed the app while it was open.
    const used = new Set();
    const doListing = async l => {
      const folder = folderFor(l, folders);
      const count = async t => ((await call('GET', `${E}/listings/${l}/${t}`)).images || []).length;
      const [phone, seven, ten] = [await count('phoneScreenshots'), await count('sevenInchScreenshots'), await count('tenInchScreenshots')];
      const tablets = seven || ten ? ` (also has ${seven} 7-inch / ${ten} 10-inch tablet screenshots, left as they are)` : '';
      if (!folder) { console.log(`  ${l}: no matching folder, left alone - ${phone} phone screenshots${tablets}`); return; }
      used.add(folder);
      await call('DELETE', `${E}/listings/${l}/phoneScreenshots`);
      for (const f of folders[folder]) {
        await call('POST', `${U}/listings/${l}/phoneScreenshots?uploadType=media`, fs.readFileSync(f), 'image/png');
      }
      console.log(`  ${l}: ${phone} phone screenshots -> ${folders[folder].length} from ${folder}/${tablets}`);
    };
    const queue = [...targets];
    const started = Date.now();
    await Promise.all(Array.from({ length: 6 }, async () => { while (queue.length) await doListing(queue.shift()); }));
    console.log(`Staged in ${Math.round((Date.now() - started) / 1000)}s.`);
    const unused = Object.keys(folders).filter(f => !used.has(f));
    if (unused.length) console.log(`\nFolders with no Play listing: ${unused.join(', ')}`);

    if (COMMIT) {
      const noReview = args.includes('--no-review');
      await call('POST', E + ':commit' + (noReview ? '?changesNotSentForReview=true' : ''));
      console.log(noReview
        ? '\nCommitted, not sent for review: Play Console -> Publishing overview -> submit when ready.'
        : '\nCommitted and sent for review.');
    } else {
      await call('DELETE', E);
      console.log('\nDry run OK: Google accepted everything, then the edit was thrown away. Nothing changed on Play.');
    }
  } catch (e) {
    await call('DELETE', E).catch(() => {});
    throw e;
  }
}

main().catch(e => { console.error('\n' + e.message); process.exit(1); });
