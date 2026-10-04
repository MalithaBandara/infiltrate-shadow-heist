import React from 'react';
import {AbsoluteFill, interpolate, random, useCurrentFrame} from 'remotion';

/** Edge darkening that ties captures from different levels into one look. */
export const Vignette: React.FC<{strength?: number}> = ({strength = 0.55}) => (
	<AbsoluteFill
		style={{
			background: `radial-gradient(ellipse at 50% 50%, rgba(0,0,0,0) 45%, rgba(0,0,0,${strength}) 100%)`,
			pointerEvents: 'none',
		}}
	/>
);

/** Film grain: an SVG noise tile re-seeded every other frame. */
export const Grain: React.FC<{opacity?: number}> = ({opacity = 0.09}) => {
	const frame = useCurrentFrame();
	const seed = Math.floor(frame / 2) % 50;
	const dx = Math.floor(random(`gx${seed}`) * 200);
	const dy = Math.floor(random(`gy${seed}`) * 200);
	return (
		<AbsoluteFill style={{opacity, mixBlendMode: 'overlay', pointerEvents: 'none', overflow: 'hidden'}}>
			<svg width="2120" height="1280" style={{position: 'absolute', left: -dx, top: -dy}}>
				<filter id={`grain${seed}`}>
					<feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" seed={seed} stitchTiles="stitch" />
					<feColorMatrix type="saturate" values="0" />
				</filter>
				<rect width="100%" height="100%" filter={`url(#grain${seed})`} />
			</svg>
		</AbsoluteFill>
	);
};

/** Letterbox bars that close in (cinematic) - value 0..1 of the target height. */
export const Letterbox: React.FC<{amount: number; size?: number}> = ({amount, size = 110}) => (
	<>
		<div style={{position: 'absolute', left: 0, right: 0, top: 0, height: size * amount, background: '#000'}} />
		<div style={{position: 'absolute', left: 0, right: 0, bottom: 0, height: size * amount, background: '#000'}} />
	</>
);

/** A white flash that decays over [frames] from the start of its Sequence. */
export const Flash: React.FC<{frames?: number; peak?: number; color?: string}> = ({frames = 8, peak = 0.85, color = '#fff'}) => {
	const frame = useCurrentFrame();
	const o = interpolate(frame, [0, frames], [peak, 0], {extrapolateRight: 'clamp'});
	return <AbsoluteFill style={{backgroundColor: color, opacity: o, pointerEvents: 'none'}} />;
};

/** Horizontal glitch slices, for a few frames around a cut. */
export const Glitch: React.FC<{frames?: number; seedKey?: string}> = ({frames = 8, seedKey = 'g'}) => {
	const frame = useCurrentFrame();
	if (frame >= frames) return null;
	const slices = Array.from({length: 7}, (_, i) => {
		const r = random(`${seedKey}-${frame}-${i}`);
		return {
			top: r * 1000,
			h: 8 + random(`${seedKey}h-${frame}-${i}`) * 60,
			dx: (random(`${seedKey}x-${frame}-${i}`) - 0.5) * 160,
			red: i % 2 === 0,
		};
	});
	return (
		<AbsoluteFill style={{pointerEvents: 'none'}}>
			{slices.map((sl, i) => (
				<div
					key={i}
					style={{
						position: 'absolute',
						left: sl.dx,
						right: -sl.dx,
						top: sl.top,
						height: sl.h,
						background: sl.red ? 'rgba(255,59,59,0.35)' : 'rgba(143,180,214,0.3)',
						mixBlendMode: 'screen',
					}}
				/>
			))}
		</AbsoluteFill>
	);
};
