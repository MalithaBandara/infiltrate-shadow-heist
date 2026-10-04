// Renders every store screenshot: bundles once, then one still per composition.
// Usage: node render.mjs [--only <substring>] [--scale <n>] [--out <dir>]
import {bundle} from '@remotion/bundler';
import {getCompositions, renderStill} from '@remotion/renderer';
import path from 'node:path';
import fs from 'node:fs';

const args = process.argv.slice(2);
const opt = (name, dflt) => {
	const i = args.indexOf(`--${name}`);
	return i >= 0 ? args[i + 1] : dflt;
};
const only = opt('only', '');
const scale = Number(opt('scale', '1'));
const outRoot = path.resolve(opt('out', 'out'));

const browserExecutable = path.resolve(
	'../playstore-video/node_modules/.remotion/chrome-headless-shell/win64/chrome-headless-shell-win64/chrome-headless-shell.exe',
);
const serveUrl = await bundle({entryPoint: path.resolve('src/index.ts')});
const comps = await getCompositions(serveUrl, {browserExecutable});

const DIRS = {
	play: 'google-play_1920x1080',
	iphone: 'app-store_iphone-6.9_2868x1320',
	ipad: 'app-store_ipad-13_2752x2064',
};
// Number each store's screenshots by their position in that store's own list, so a partial
// render (--only) still writes the right file names.
const counters = {};
const jobs = [];
for (const c of comps) {
	const {shot, fmt} = c.defaultProps;
	const store = fmt === 'play' ? 'play' : 'apple';
	if (!shot.stores.includes(store)) continue;
	counters[fmt] = (counters[fmt] ?? 0) + 1;
	jobs.push({c, shot, fmt, n: counters[fmt]});
}
for (const {c, shot, fmt, n} of jobs) {
	if (only && !c.id.includes(only)) continue;
	const name = `${String(n).padStart(2, '0')}_${shot.id.replace(/^\d+_/, '')}.png`;
	const dir = path.join(outRoot, DIRS[fmt]);
	fs.mkdirSync(dir, {recursive: true});
	const output = path.join(dir, name);
	await renderStill({composition: c, serveUrl, output, imageFormat: 'png', scale, browserExecutable});
	console.log(output);
}
