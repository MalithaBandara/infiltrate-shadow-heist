import React from 'react';
import {AbsoluteFill, interpolate, OffthreadVideo, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';

export type FootageProps = {
	/** File under public/clips/. */
	clip: string;
	/** Where in the clip this shot starts, in seconds. */
	from: number;
	/**
	 * Horizontal framing of the 2340x1080 capture inside the 16:9 frame: 0 = left edge, 50 = centre,
	 * 100 = right edge. The captures are the phone's 2.17:1 aspect, so ~210px per side is spare.
	 */
	focusX?: number;
	/** Slow push-in over the shot: [startScale, endScale]. */
	zoom?: [number, number];
	/** Zoom anchor, in % of the frame. */
	origin?: [number, number];
	/** Playback rate (0.5 = slow motion). */
	rate?: number;
	dim?: number;
	blur?: number;
};

export const Footage: React.FC<FootageProps> = ({
	clip,
	from,
	focusX = 50,
	zoom = [1.0, 1.04],
	origin = [50, 50],
	rate = 1,
	dim = 0,
	blur = 0,
}) => {
	const frame = useCurrentFrame();
	const {durationInFrames, fps} = useVideoConfig();
	const scale = interpolate(frame, [0, durationInFrames], zoom, {extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{backgroundColor: '#000', overflow: 'hidden'}}>
			<AbsoluteFill style={{transform: `scale(${scale})`, transformOrigin: `${origin[0]}% ${origin[1]}%`}}>
				<OffthreadVideo
					src={staticFile(`clips/${clip}`)}
					startFrom={Math.round(from * fps)}
					playbackRate={rate}
					muted
					style={{
						width: '100%',
						height: '100%',
						objectFit: 'cover',
						objectPosition: `${focusX}% 50%`,
						filter: blur > 0 ? `blur(${blur}px)` : undefined,
					}}
				/>
			</AbsoluteFill>
			{dim > 0 ? <AbsoluteFill style={{backgroundColor: `rgba(0,0,0,${dim})`}} /> : null}
		</AbsoluteFill>
	);
};
