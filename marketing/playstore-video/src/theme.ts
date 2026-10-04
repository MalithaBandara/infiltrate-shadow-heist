import {continueRender, delayRender, staticFile} from 'remotion';

export const FPS = 60;
export const W = 1920;
export const H = 1080;

/** Seconds to frames at the composition rate. */
export const s = (sec: number) => Math.round(sec * FPS);

export const C = {
	ink: '#04060A',
	paper: '#EEF3F8',
	dim: 'rgba(238,243,248,0.62)',
	torch: '#FFD98A',
	alert: '#FF3B3B',
	cold: '#8CC4FF',
	stamp: '#C8201E',
};

export const DISPLAY = 'InfDisplay';
export const HAND = 'InfHand';

export const TEXT_SHADOW = '0 6px 40px rgba(0,0,0,0.65), 0 2px 6px rgba(0,0,0,0.5)';

const fonts: [string, string][] = [
	[DISPLAY, 'fonts/BebasNeue-Regular.ttf'],
	[HAND, 'fonts/handwritten.ttf'],
];

if (typeof document !== 'undefined') {
	const handle = delayRender('Loading fonts');
	Promise.all(
		fonts.map(([family, file]) => {
			const face = new FontFace(family, `url('${staticFile(file)}') format('truetype')`);
			return face.load().then((f) => document.fonts.add(f));
		}),
	)
		.then(() => continueRender(handle))
		.catch((err) => {
			console.error(err);
			continueRender(handle);
		});
}
