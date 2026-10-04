import React from 'react';
import {Composition} from 'remotion';
import {Promo, PROMO_FRAMES} from './Promo';
import {FPS, H, W} from './theme';

export const RemotionRoot: React.FC = () => (
	<>
		<Composition id="Promo" component={Promo} durationInFrames={PROMO_FRAMES} fps={FPS} width={W} height={H} />
		<Composition id="PromoVertical" component={Promo} durationInFrames={PROMO_FRAMES} fps={FPS} width={H} height={W} />
	</>
);
