import {loadFont as loadBebas} from '@remotion/google-fonts/BebasNeue';
import {loadFont as loadMono} from '@remotion/google-fonts/ShareTechMono';

export const {fontFamily: TITLE_FONT} = loadBebas();
export const {fontFamily: MONO_FONT} = loadMono();

export const FPS = 60;
export const WIDTH = 1920;
export const HEIGHT = 1080;

export const COLORS = {
	ink: '#07090d',
	white: '#f2f5f8',
	// The game's lamp glow and its laser red - the only two warm notes in a blue-black world.
	amber: '#ffb347',
	alarm: '#ff3b3b',
	steel: '#8fb4d6',
};

/** Seconds to frames. */
export const s = (seconds: number) => Math.round(seconds * FPS);
