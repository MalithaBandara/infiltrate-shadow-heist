import React from 'react';
import {Composition} from 'remotion';
import {FORMATS, FormatId, SHOTS} from './data';
import {Screenshot} from './Screenshot';

/** One single-frame composition per screenshot per store size: `<format>-<shot id>`. */
export const RemotionRoot: React.FC = () => (
	<>
		{(Object.keys(FORMATS) as FormatId[]).flatMap((fmt) =>
			SHOTS.map((shot) => (
				<Composition
					key={`${fmt}-${shot.id}`}
					id={`${fmt}-${shot.id.replace('_', '-')}`}
					component={Screenshot}
					defaultProps={{shot, fmt}}
					durationInFrames={1}
					fps={30}
					width={FORMATS[fmt].w}
					height={FORMATS[fmt].h}
				/>
			)),
		)}
	</>
);
