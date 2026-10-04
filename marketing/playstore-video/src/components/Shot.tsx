import React from 'react';
import {AbsoluteFill, Easing, interpolate, OffthreadVideo, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {SHOTS, ShotDef, ShotId} from '../shots';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/** Horizontal crop centre (0..1 of the source width) at `t` seconds into the shot. */
function cropCentreAt(pf: ShotDef['pfocus'], t: number): number {
	if (pf == null) return 0.5;
	if (typeof pf === 'number') return pf;
	if (pf.length === 1) return pf[0][1];
	return interpolate(
		t,
		pf.map((k) => k[0]),
		pf.map((k) => k[1]),
		{...clamp, easing: Easing.inOut(Easing.sin)},
	);
}

/**
 * One gameplay shot, cropped to fill its box (cover) and slowly pushed in around its focus
 * point. `dur` is the on-screen length in frames, used only to spread the push across the shot.
 * `offset` skips extra source frames (for continuing a shot that started inside another layout).
 *
 * In a portrait box (the 9:16 cut) the crop is a narrow window of the landscape source. It sits on
 * the source centre by default - the game camera keeps the player centred horizontally - or
 * follows the shot's `pfocus` keyframes where it needs to show something else.
 */
export const Shot: React.FC<{
	id: ShotId;
	dur: number;
	offset?: number;
	style?: React.CSSProperties;
	filter?: string;
	/** Overrides the shot's own push (e.g. a grid tile that must hold still). */
	zoom?: [number, number];
	/** The box this shot fills, when it isn't the whole video frame (grid tiles). */
	box?: {w: number; h: number};
}> = ({id, dur, offset = 0, style, filter, zoom, box}) => {
	const def: ShotDef = SHOTS[id];
	const frame = useCurrentFrame();
	const {fps, width, height} = useVideoConfig();
	const bw = box?.w ?? width;
	const bh = box?.h ?? height;
	const portrait = bh > bw;
	const [z0, z1] = zoom ?? (portrait ? def.pzoom ?? [1.0, 1.05] : def.zoom ?? [1.04, 1.1]);
	const [fx, fy] = def.focus ?? [0.5, 0.5];
	const z = interpolate(frame, [0, dur], [z0, z1], clamp);

	let posX = fx;
	let originX = fx;
	if (portrait) {
		// object-position percentages place the crop window, not its centre: convert.
		const iw = Math.max(bw, bh * (def.aspect ?? 16 / 9));
		const c = cropCentreAt(def.pfocus, frame / fps);
		posX = iw > bw ? Math.min(1, Math.max(0, (c * iw - bw / 2) / (iw - bw))) : 0.5;
		originX = 0.5;
	}
	return (
		<AbsoluteFill style={{overflow: 'hidden', backgroundColor: '#000', ...style}}>
			<OffthreadVideo
				src={staticFile(def.src)}
				trimBefore={Math.round(def.from * fps) + offset}
				playbackRate={def.rate ?? 1}
				muted
				style={{
					width: '100%',
					height: '100%',
					objectFit: 'cover',
					objectPosition: `${posX * 100}% ${fy * 100}%`,
					transform: `scale(${z})`,
					transformOrigin: `${originX * 100}% ${fy * 100}%`,
					filter,
				}}
			/>
		</AbsoluteFill>
	);
};
