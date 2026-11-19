// Renders one vertical short, or all of them.
//   npm run short -- near-miss            540p draft  -> out/shorts/near-miss_draft.mp4
//   npm run short -- near-miss --final    1080p60     -> out/shorts/near-miss.mp4
//   npm run short -- all [--final]
import {spawnSync} from 'node:child_process';
import {readFileSync} from 'node:fs';

const args = process.argv.slice(2);
const final = args.includes('--final');
const ids = args.filter((a) => !a.startsWith('--'));
if (ids.length === 0) {
	console.error('Usage: npm run short -- <id|all> [--final]');
	process.exit(1);
}

const all = [...readFileSync('src/shorts/shorts.ts', 'utf8').matchAll(/^\t\tid: '([\w-]+)'/gm)].map((m) => m[1]);
const targets = ids.includes('all') ? all : ids;
for (const id of targets) {
	if (!all.includes(id)) {
		console.error(`No short "${id}". Known: ${all.join(', ')}`);
		process.exit(1);
	}
	const out = `out/shorts/${id}${final ? '' : '_draft'}.mp4`;
	const flags = final ? ['--crf=16'] : ['--scale=0.5', '--crf=23'];
	// One command string (ids are checked against shorts.ts above), so npx resolves on Windows too.
	const cmd = ['npx', 'remotion', 'render', `Short-${id}`, out, '--codec=h264', ...flags].join(' ');
	const r = spawnSync(cmd, {stdio: 'inherit', shell: true});
	if (r.status !== 0) process.exit(r.status ?? 1);
}
