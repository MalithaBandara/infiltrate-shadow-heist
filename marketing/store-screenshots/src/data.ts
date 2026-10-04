/**
 * Store screenshot set. Every frame under public/frames/ is real gameplay captured from the JVM
 * build at 2304x1296 (capture/capture_level.ps1), except push_l8 and caught_l8, which are stills
 * from the level 8 recording made for the video.
 *
 * Copy rules (Google Play preview-asset policy, applies to every store here for simplicity): no
 * "free", "best", "#1", "top", "new", "sale", no calls to action, no device frames or badges.
 */
export type FormatId = 'play' | 'iphone' | 'ipad';

export const FORMATS: Record<FormatId, {w: number; h: number; dir: string}> = {
	// Google Play phone + 7"/10" tablet: 16:9, >= 1920x1080 keeps the game eligible for promotion.
	play: {w: 1920, h: 1080, dir: 'google-play_1920x1080'},
	// App Store 6.9" iPhone, landscape. Apple scales it down for every smaller iPhone.
	iphone: {w: 2868, h: 1320, dir: 'app-store_iphone-6.9_2868x1320'},
	// App Store 13" iPad, landscape. Scaled down for every smaller iPad.
	ipad: {w: 2752, h: 2064, dir: 'app-store_ipad-13_2752x2064'},
};

export type Shot = {
	id: string;
	src: string;
	tag?: string;
	title: string[];
	accent?: string;
	accentColor?: string;
	sub: string;
	/** Caption corner in the full-bleed (phone) layouts. */
	side?: 'left' | 'right';
	vpos?: 'top' | 'bottom';
	/** Crop anchor as fractions of the source frame. */
	focus?: [number, number];
	/** Extra zoom per format (1 = cover-fit). */
	zoom?: Partial<Record<FormatId, number>>;
	kind?: 'hero' | 'collage' | 'gadgets' | 'news';
	/** Store presence: Play allows 8 phone screenshots, the App Store 10. */
	stores: ('play' | 'apple')[];
};

export const TILES = [
	{src: 'frames/tile_l2.png', label: '02  CARGO YARD', x: 0.5},
	{src: 'frames/tile_l3.png', label: '03  FIRST CONTACT', x: 0.73},
	{src: 'frames/tile_l7.png', label: '07  SERVICE TUNNEL', x: 0.48},
	{src: 'frames/tile_l10.png', label: '10  BELOW THE YARD', x: 0.56},
];

export const GADGETS = [
	{img: 'img/gadget_invis.png', name: 'INVISIBILITY CLOAK'},
	{img: 'img/gadget_lasershield.png', name: 'GUARD SHIELD'},
	{img: 'img/gadget_boots.png', name: 'STEALTH BOOTS'},
	{img: 'img/gadget_checkpoints.png', name: 'CHECKPOINTS'},
	{img: 'img/gadget_checkpoint.png', name: 'REMOTE TRIGGER'},
];

export const SHOTS: Shot[] = [
	{
		id: '01_hero',
		src: 'frames/hero_l5.png',
		kind: 'hero',
		title: ['BECOME THE SHADOW'],
		accent: 'SHADOW',
		sub: 'A silhouette stealth platformer across 12 night missions.',
		focus: [0.5, 0.42],
		stores: ['play', 'apple'],
	},
	{
		id: '02_stealth',
		src: 'frames/stealth_l3.png',
		tag: 'STEALTH',
		title: ['STAY OUT OF', 'THE LIGHT'],
		accent: 'LIGHT',
		sub: 'Guards only see what their torch beam touches.',
		focus: [0.6, 0.62],
		zoom: {play: 1.12, iphone: 1.08, ipad: 1.08},
		stores: ['play', 'apple'],
	},
	{
		id: '03_missions',
		src: 'frames/tile_l2.png',
		kind: 'collage',
		title: ['12 MISSIONS'],
		accent: '12',
		sub: 'From fog-bound yards to the tunnels beneath them.',
		stores: ['play', 'apple'],
	},
	{
		id: '04_escort',
		src: 'frames/escort_l11.png',
		tag: 'ESCORT',
		title: ['BREAK HIM OUT'],
		accent: 'OUT',
		sub: 'Lead the prisoner past locked doors, lifts and patrols.',
		vpos: 'bottom',
		focus: [0.45, 0.45],
		stores: ['play', 'apple'],
	},
	{
		id: '05_push',
		src: 'frames/push_l8.png',
		tag: 'PUZZLES',
		title: ['PUSH, CLIMB', '& SWING'],
		accent: 'SWING',
		sub: 'Carts, cranes and hanging loads become your way in.',
		focus: [0.55, 0.62],
		zoom: {play: 1.1, iphone: 1.05, ipad: 1.05},
		stores: ['play', 'apple'],
	},
	{
		id: '06_storm',
		src: 'frames/storm_l2.png',
		tag: 'NIGHT RUNS',
		title: ['OUTRUN', 'THE STORM'],
		accent: 'STORM',
		sub: 'Rooftop leaps through rain and lightning.',
		focus: [0.55, 0.45],
		stores: ['play', 'apple'],
	},
	{
		id: '07_tunnels',
		src: 'frames/tunnel_l7.png',
		tag: 'HAZARDS',
		title: ['LEAN INTO', 'THE WIND'],
		accent: 'WIND',
		sub: 'Turbine tunnels, steam vents and patrol drones.',
		focus: [0.5, 0.5],
		zoom: {play: 1.06, iphone: 1.0, ipad: 1.0},
		stores: ['apple'],
	},
	{
		id: '08_gadgets',
		src: 'frames/gadgets_l2_ui.png',
		kind: 'gadgets',
		tag: 'GADGETS',
		title: ['VANISH ON', 'COMMAND'],
		accent: 'VANISH',
		sub: 'Cloak, shield, stealth boots and more.',
		side: 'right',
		// Bottom-anchored: the 2.17:1 iPhone crop must keep the touch controls, not the objectives.
		focus: [0.5, 1.0],
		stores: ['play', 'apple'],
	},
	{
		id: '09_caught',
		src: 'frames/caught_l8.png',
		kind: 'news',
		tag: 'STAKES',
		title: ['GET CAUGHT,', 'MAKE HEADLINES'],
		accent: 'HEADLINES',
		accentColor: '#E5463D',
		sub: 'Every guard, camera and laser is watching.',
		stores: ['play', 'apple'],
	},
];
