import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame, Easing} from 'remotion';

/**
 * Shows its children only inside a soft torch-light spot that drifts along [path] and then opens
 * to the full frame - the game's own "seen or unseen" idea as a reveal.
 */
export const TorchReveal: React.FC<{
	children: React.ReactNode;
	/** Keyframes of the spot centre, in px: [frame, x, y]. */
	path: [number, number, number][];
	radius?: number;
	openAt: number;
	openFrames?: number;
}> = ({children, path, radius = 260, openAt, openFrames = 16}) => {
	const frame = useCurrentFrame();
	const fs = path.map((p) => p[0]);
	const x = interpolate(frame, fs, path.map((p) => p[1]), {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.inOut(Easing.sin)});
	const y = interpolate(frame, fs, path.map((p) => p[2]), {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.inOut(Easing.sin)});
	const open = interpolate(frame, [openAt, openAt + openFrames], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.in(Easing.cubic)});
	const r = radius + open * 2200;
	const flicker = 1 + 0.03 * Math.sin(frame * 1.7) * Math.sin(frame * 0.37);
	const mask = `radial-gradient(circle ${r * flicker}px at ${x}px ${y}px, #000 0%, #000 55%, rgba(0,0,0,0.35) 80%, transparent 100%)`;
	return (
		<AbsoluteFill style={{backgroundColor: '#000'}}>
			<AbsoluteFill style={{WebkitMaskImage: mask, maskImage: mask}}>{children}</AbsoluteFill>
		</AbsoluteFill>
	);
};
