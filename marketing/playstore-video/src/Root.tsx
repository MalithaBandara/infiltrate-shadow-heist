import React from 'react';
import {Composition, Folder} from 'remotion';
import {Promo, PROMO_FRAMES} from './Promo';
import {Short} from './shorts/Short';
import {SHORTS} from './shorts/shorts';
import {shortFrames} from './shorts/types';
import {FPS, H, W} from './theme';

export const RemotionRoot: React.FC = () => (
	<>
		<Composition id="Promo" component={Promo} durationInFrames={PROMO_FRAMES} fps={FPS} width={W} height={H} />
		<Composition id="PromoVertical" component={Promo} durationInFrames={PROMO_FRAMES} fps={FPS} width={H} height={W} />
		<Folder name="Shorts">
			{SHORTS.map((d) => (
				<Composition
					key={d.id}
					id={`Short-${d.id}`}
					component={Short}
					defaultProps={{def: d}}
					durationInFrames={shortFrames(d)}
					fps={FPS}
					width={H}
					height={W}
				/>
			))}
		</Folder>
	</>
);
