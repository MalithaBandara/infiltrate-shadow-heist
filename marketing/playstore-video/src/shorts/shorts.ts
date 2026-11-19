/**
 * Every vertical short (TikTok, Reels, YouTube Shorts). Scripts and status: marketing/shorts/SHORTS_PLAN.md.
 * Times in seconds. Clip `from`/`to` are source in/out points; caption `at`/`out` are on the
 * short's own timeline. On-screen copy: no em dashes.
 */
import {C} from '../theme';
import {Clip, ShortDef} from './types';

const IPHONE = 2688 / 1242;
/** Old autopilot captures (phone aspect). Swap for fresh takes as they come in. */
const temp = (name: string) => ({src: `footage/temp/${name}.mp4`, aspect: IPHONE});
const l1 = (from: number, to: number, extra: Partial<Clip> = {}): Clip => ({...temp('l1'), from, to, ...extra});
const l2 = (from: number, to: number, extra: Partial<Clip> = {}): Clip => ({...temp('l2'), from, to, ...extra});
const l4 = (from: number, to: number, extra: Partial<Clip> = {}): Clip => ({...temp('l4'), from, to, ...extra});
const l3 = (from: number, to: number, extra: Partial<Clip> = {}): Clip => ({...temp('l3'), from, to, ...extra});
const l5 = (from: number, to: number, extra: Partial<Clip> = {}): Clip => ({...temp('l5'), from, to, ...extra});

/** Over the solid black rooftops of level 5, clear of the hooks and the jumping player. */
const LOW = 1010;
/** Over the harbour below level 2's hanging loads, clear of the player running on top. */
const HARBOUR = 1180;

export const SHORTS: ShortDef[] = [
	{
		// Crouched behind a crate: the guard below walks right under, then his partner on the
		// hanging load sweeps his torch across the crate. Square window so the partner is in frame.
		// (18.9 s on is not usable: an alert marker pops over a guard outside the crop.)
		id: 'near-miss',
		clips: [l3(9.7, 15.9, {square: 0.635, pzoom: [1.0, 1.05]})],
		captions: [
			{at: 0.05, out: 2.4, lines: ['HE NEVER', 'LOOKED UP.'], size: 115},
			{at: 2.65, out: 4.5, lines: ['HIS PARTNER', 'DID.'], accent: 'DID', size: 115},
			{at: 4.7, lines: ['THE CRATE', 'SAVED ME.'], size: 115},
		],
		sfx: [{src: 'sfx/guard_investigate.wav', at: 2.7, volume: 0.5}],
	},
	{
		// Three crane-hook swings in a row along the moonlit rooftops, sped up 1.2x: the same clip
		// cut faster is what took one dev's TikTok from 10k to 7M views (SHORTS_PLAN.md, research).
		id: 'hook-swing',
		// The replay idles ~2 s on a block after the first swing (18.1 to 19.9 s): cut, same spot.
		clips: [l5(15.6, 18.1, {rate: 1.2, pzoom: [1.0, 1.03]}), l5(19.9, 25.6, {rate: 1.2, pzoom: [1.03, 1.06]})],
		captions: [
			{at: 0.05, lines: ['YOU CAN SWING', 'ON CRANE HOOKS.'], top: LOW, size: 150},
			{at: 1.08, out: 1.6, lines: ['1'], kind: 'pop'},
			{at: 2.58, out: 3.1, lines: ['2'], kind: 'pop'},
			{at: 5.5, out: 6.0, lines: ['3'], kind: 'pop'},
		],
	},
	{
		// Run, leap into the pillar field, freeze mid-air with the question, then the clean answer.
		id: 'would-you',
		clips: [l5(34.85, 35.8), l5(35.8, 35.8, {hold: 1.6}), l5(35.8, 39.3)],
		captions: [
			{at: 0.05, out: 2.45, lines: ['WOULD YOU MAKE', 'THIS JUMP?'], top: LOW, size: 160, accent: 'JUMP'},
			{at: 4.0, lines: ['NO STOPS.'], top: LOW, size: 170},
		],
	},
	{
		// Crouch-walking under low slabs in the dark spotlight section, lasers ahead; stands once clear.
		id: 'dont-stand-up',
		clips: [l4(32.5, 40.3, {rate: 1.15})],
		captions: [
			{at: 0.05, out: 5.3, lines: ["DON'T.", 'STAND.', 'UP.'], size: 150},
			{at: 5.6, lines: ['NOW YOU', 'CAN.'], size: 150},
		],
	},
	{
		// Hopping the hanging loads in the rain; lightning strikes at 12.33 s in the source.
		id: 'storm-run',
		clips: [l2(7.4, 13.4)],
		captions: [
			{at: 0.05, out: 4.6, lines: ['THE GROUND IS', 'OFF LIMITS.'], top: HARBOUR, size: 150},
			{at: 4.95, lines: ['THEN THE', 'STORM HIT.'], accent: 'STORM', top: HARBOUR, size: 150},
		],
		flashes: [4.93],
		sfx: [
			{src: 'sfx/loop_rain.wav', at: 0, volume: 0.45, dur: 6.0},
			{src: 'sfx/thunder.wav', at: 4.95, volume: 0.9},
		],
	},
	{
		// Vault the crates and run the roofs of a parked truck convoy.
		id: 'truck-run',
		clips: [l1(2.0, 9.4, {rate: 1.2})],
		captions: [
			{at: 0.05, out: 2.5, lines: ['THEY PARKED', 'THE TRUCKS.'], size: 150},
			{at: 2.85, lines: ['SO I USED', 'THEM.'], size: 150},
		],
	},
	{
		// Story part 1, told in captions (swap in a VO or presenter later): the job, the target,
		// then lightning and the twist.
		id: 'story-1',
		// Level start: the camera only starts following at ~2.6 s, so pan the crop onto the player.
		clips: [
			l1(0.0, 2.6, {pfocus: [[0, 0.34], [1.5, 0.35], [2.2, 0.43], [2.6, 0.5]]}),
			l1(9.8, 13.4, {flash: 0.2}),
			l2(11.9, 16.4),
		],
		captions: [
			{at: 0.05, out: 2.35, lines: ['THE JOB WAS', 'SIMPLE.'], size: 150},
			{at: 2.7, out: 5.9, lines: ['GET IN. FIND', 'CONTAINER 17.'], accent: '17', size: 130},
			{at: 6.65, out: 8.3, lines: ['BUT WHEN I', 'GOT THERE...'], top: HARBOUR, size: 140},
			{at: 8.6, lines: ['IT WAS', 'GONE.'], accent: 'GONE', top: HARBOUR, size: 150},
		],
		flashes: [6.63],
		sfx: [
			{src: 'sfx/loop_rain.wav', at: 6.2, volume: 0.45, dur: 4.5},
			{src: 'sfx/thunder.wav', at: 6.65, volume: 0.9},
		],
	},
];

export const ACCENT = C.torch;
