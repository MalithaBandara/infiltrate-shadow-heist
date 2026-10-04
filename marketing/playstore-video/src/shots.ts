/**
 * Every gameplay shot the edit uses, keyed by the shot-list id (SHOT_LIST.md). Both the 16:9 and
 * the 9:16 cut read from here.
 *
 * TEMP: all sources below are stand-ins cut from the old 26/30 Sep captures so the motion
 * graphics can be built and timed. They get replaced one by one with the fresh takes from
 * screenshots/promo_1080p/ (linked into public/footage/).
 */
export type ShotDef = {
	/** Path under public/. */
	src: string;
	/** In-point in the source, seconds. */
	from: number;
	/** Playback speed; < 1 is slow motion. */
	rate?: number;
	/** Source width / height. Fresh takes are 16:9; the old iPhone-sized captures are wider. */
	aspect?: number;
	/** 16:9 cut: scale at the shot's first and last frame (Ken Burns push). */
	zoom?: [number, number];
	/** 16:9 cut: zoom anchor / crop centre as fractions of the frame (y is used by both cuts). */
	focus?: [number, number];
	/** 9:16 cut: scale at first and last frame. */
	pzoom?: [number, number];
	/**
	 * 9:16 cut: horizontal crop centre as a fraction of the source width - a constant, or
	 * [seconds into the shot, centre] keyframes for a pan. Defaults to 0.5 (the camera keeps the
	 * player centred everywhere except at a level's two ends).
	 */
	pfocus?: number | [number, number][];
};

const IPHONE = 2688 / 1242;
const t = (name: string) => ({src: `footage/temp/${name}.mp4`, aspect: IPHONE});
const ta = (name: string) => ({src: `footage/temp/${name}.mp4`});
/** Fresh 2304x1296 takes, hard-linked from screenshots/promo_1080p/ as footage/fresh/<shot>.mp4. */
const f = (name: string) => ({src: `footage/fresh/${name}.mp4`});

export const SHOTS = {
	// Hook
	S01: {...t('l5'), from: 36.2, zoom: [1.22, 1.3], focus: [0.42, 0.5]},
	S02: {...t('l5'), from: 16.3, rate: 0.8, zoom: [1.3, 1.22], focus: [0.62, 0.35]},
	// Every light is a trap
	S03: {...t('l3'), from: 10.2, rate: 0.85, zoom: [1.12, 1.24], focus: [0.5, 0.6], pfocus: [[0, 0.46], [2.5, 0.52]]},
	// Montage
	S05: {...t('l3'), from: 2.0, zoom: [1.25, 1.32], focus: [0.4, 0.75]},
	S06: {...t('l3'), from: 5.3, zoom: [1.25, 1.32], focus: [0.5, 0.55]},
	S07: {...t('l2'), from: 11.4, zoom: [1.15, 1.22], focus: [0.5, 0.5]},
	// Level 8, S08_push_take4: reaches the cart, leans in to brace, pushes (hanging loads overhead).
	S08: {...f('S08'), from: 4.85, zoom: [1.42, 1.5], focus: [0.53, 0.76], pzoom: [1.02, 1.07], pfocus: 0.54},
	S11: {...t('l7'), from: 6.0, zoom: [1.1, 1.18], focus: [0.5, 0.5]},
	// Grid tiles
	G01: {...t('l1'), from: 3.0},
	G02: {...t('l2'), from: 20.0},
	G05: {...ta('a_l5'), from: 20.0},
	G06: {...t('l6'), from: 6.0},
	G11: {...ta('a_l11'), from: 5.0},
	// Don't get seen (also the grid tile the push-in lands on, so its crop centre stays 0.5)
	S16: {...t('l4'), from: 8.0, zoom: [1.05, 1.16], focus: [0.5, 0.5]},
	S17: {...t('l3'), from: 20.2, zoom: [1.3, 1.4], focus: [0.4, 0.45]},
	// Escort
	S19: {...ta('a_l11'), from: 0.5, zoom: [1.15, 1.25], focus: [0.35, 0.3]},
	// Gadgets
	S21: {...t('l3'), from: 24.0, zoom: [1.1, 1.15]},
	// End card background
	END: {...ta('a_l5'), from: 2.0, zoom: [1.0, 1.06]},
} satisfies Record<string, ShotDef>;

export type ShotId = keyof typeof SHOTS;
