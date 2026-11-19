import {ShotDef} from '../shots';
import {FPS} from '../theme';

/** One piece of gameplay in a short: a source range played through, or a single frame held. */
export type Clip = ShotDef & {
	/** Source out-point, seconds. Ignored when `hold` is set. */
	to: number;
	/** Freeze the frame at `from` for this many seconds instead of playing. */
	hold?: number;
	/** White flash as the clip cuts in, 0..1. */
	flash?: number;
	/**
	 * Show the clip in a square window instead of full height, centred on this point (0..1 of the
	 * source width), over a blurred copy. For moments that need more width than the 9:16 crop
	 * gives (the player and a guard a third of the screen away). Captions go above the window.
	 */
	square?: number;
};

export type Caption = {
	/** Seconds on the short's timeline. */
	at: number;
	/** Seconds on the short's timeline; omitted = stays until the end card. */
	out?: number;
	lines: string[];
	size?: number;
	/** Word drawn in the torch colour. */
	accent?: string;
	/** Distance of the first line from the top of the 1920 px frame. */
	top?: number;
	/**
	 * kinetic: words rise in, the default. glitch: alarm-style split text. pop: a big word on the
	 * left edge that springs in and fades (counters).
	 */
	kind?: 'kinetic' | 'glitch' | 'pop';
};

export type ShortDef = {
	/** Composition id is `Short-<id>`; also the output file name. */
	id: string;
	clips: Clip[];
	captions: Caption[];
	/** Voiceover under public/, e.g. 'vo/story-1.mp3'. */
	vo?: string;
	/**
	 * AI presenter clip under public/ (1080x1920). key: flat green background keyed out, presenter
	 * stands bottom-left over the gameplay. box: picture-in-picture frame. Its audio is the VO.
	 */
	presenter?: {src: string; mode?: 'key' | 'box'; at?: number};
	/** Sound effects under public/sfx, at seconds on the timeline. */
	sfx?: {src: string; at: number; volume?: number; dur?: number}[];
	/** Extra white flashes (lightning) at seconds on the timeline. */
	flashes?: number[];
	/** End card length in seconds (0 = none). */
	end?: number;
	endTag?: string;
};

export const SAFE_TOP = 300;
/** Top edge of a `square` clip's window: leaves two caption lines above it. */
export const SQUARE_TOP = 540;

/** On-screen length of a clip in frames. */
export const clipFrames = (c: Clip) => Math.round((c.hold ?? (c.to - c.from) / (c.rate ?? 1)) * FPS);

/** Long enough for the store line to sit readable on screen for about 2 s. */
export const END_DEFAULT = 2.4;

export function shortFrames(d: ShortDef): number {
	return d.clips.reduce((n, c) => n + clipFrames(c), 0) + Math.round((d.end ?? END_DEFAULT) * FPS);
}
