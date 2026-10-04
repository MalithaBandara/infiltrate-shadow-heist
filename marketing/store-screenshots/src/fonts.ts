import {loadFont} from '@remotion/google-fonts/BarlowSemiCondensed';
import {continueRender, delayRender, staticFile} from 'remotion';

/** Display face: the game's own Bebas Neue. */
export const DISPLAY = 'InfDisplay';
/** Text face for the supporting lines: a condensed grotesque that sits well under Bebas. */
export const {fontFamily: TEXT} = loadFont('normal', {weights: ['500', '600', '700'], subsets: ['latin']});

if (typeof document !== 'undefined') {
	const handle = delayRender('Loading Bebas Neue');
	new FontFace(DISPLAY, `url('${staticFile('fonts/BebasNeue-Regular.ttf')}') format('truetype')`)
		.load()
		.then((f) => {
			document.fonts.add(f);
			continueRender(handle);
		})
		.catch((e) => {
			console.error(e);
			continueRender(handle);
		});
}
