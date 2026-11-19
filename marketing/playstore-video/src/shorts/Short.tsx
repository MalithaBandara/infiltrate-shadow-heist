import React, {useCallback, useRef} from 'react';
import {
	AbsoluteFill,
	Audio,
	Freeze,
	interpolate,
	OffthreadVideo,
	Sequence,
	spring,
	staticFile,
	useCurrentFrame,
	useVideoConfig,
} from 'remotion';
import {Flash, GlitchText, Grain} from '../components/Fx';
import {Kinetic} from '../components/Kinetic';
import {EndCard} from '../components/Scenes';
import {Shot} from '../components/Shot';
import {C, DISPLAY, s, TEXT_SHADOW} from '../theme';
import {Caption, Clip, clipFrames, END_DEFAULT, SAFE_TOP, ShortDef, SQUARE_TOP} from './types';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const LEFT = 80;

/** A big word on the left edge that springs in and fades: counters, single beats. */
const Pop: React.FC<{text: string; dur: number; top: number; size: number}> = ({text, dur, top, size}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const p = spring({frame, fps, config: {damping: 11, stiffness: 260, mass: 0.6}});
	const o = interpolate(frame, [dur - 10, dur], [1, 0], clamp);
	return (
		<div
			style={{
				position: 'absolute',
				left: LEFT,
				top,
				fontFamily: DISPLAY,
				fontSize: size,
				lineHeight: 1,
				color: C.torch,
				textShadow: `0 0 40px ${C.torch}, ${TEXT_SHADOW}`,
				transform: `scale(${interpolate(p, [0, 1], [1.8, 1])})`,
				transformOrigin: 'left center',
				opacity: o * Math.min(1, p * 2),
			}}
		>
			{text}
		</div>
	);
};

const CaptionLayer: React.FC<{c: Caption; dur: number}> = ({c, dur}) => {
	const top = c.top ?? SAFE_TOP;
	const size = c.size ?? 140;
	const kind = c.kind ?? 'kinetic';
	const outF = c.out == null ? undefined : s(c.out - c.at);
	if (kind === 'pop') return <Pop text={c.lines.join(' ')} dur={dur} top={top} size={size * 2.4} />;
	if (kind === 'glitch') return <GlitchText text={c.lines.join('\n')} at={0} out={outF} size={size} style={{left: LEFT, top}} />;
	return (
		<Kinetic
			lines={c.lines.map((text) => ({text, size, accent: c.accent ? {word: c.accent, color: C.torch} : undefined}))}
			at={0}
			out={outF}
			style={{left: LEFT, top, whiteSpace: 'nowrap'}}
		/>
	);
};

/**
 * Keys a flat green background out of a presenter clip, frame by frame, onto a canvas. The video
 * itself stays invisible but still plays its audio.
 */
const KeyedPresenter: React.FC<{src: string}> = ({src}) => {
	const canvas = useRef<HTMLCanvasElement>(null);
	const {height} = useVideoConfig();
	const onVideoFrame = useCallback((frame: CanvasImageSource) => {
		const cv = canvas.current;
		const ctx = cv?.getContext('2d', {willReadFrequently: true});
		if (!cv || !ctx) return;
		ctx.drawImage(frame, 0, 0, cv.width, cv.height);
		const img = ctx.getImageData(0, 0, cv.width, cv.height);
		const d = img.data;
		for (let i = 0; i < d.length; i += 4) {
			const r = d[i];
			const g = d[i + 1];
			const b = d[i + 2];
			const m = Math.max(r, b);
			const k = g - m; // how much greener than the other channels
			if (k > 70) d[i + 3] = 0;
			else if (k > 25) d[i + 3] = Math.round((255 * (70 - k)) / 45);
			if (k > 0) d[i + 1] = m; // despill: no green fringe on hair and edges
		}
		ctx.putImageData(img, 0, 0);
	}, []);
	// Presenter stands in the bottom-left, about 55% of the frame tall, feet at the bottom edge.
	const h = Math.round(height * 0.56);
	const w = Math.round((h * 9) / 16);
	return (
		<AbsoluteFill>
			<OffthreadVideo src={staticFile(src)} onVideoFrame={onVideoFrame} style={{opacity: 0, width: 1, height: 1}} />
			<canvas
				ref={canvas}
				width={w}
				height={h}
				style={{position: 'absolute', left: -20, bottom: 0, width: w, height: h, filter: 'drop-shadow(0 0 30px rgba(0,0,0,0.6))'}}
			/>
		</AbsoluteFill>
	);
};

const BoxPresenter: React.FC<{src: string}> = ({src}) => (
	<div
		style={{
			position: 'absolute',
			left: LEFT,
			bottom: 470,
			width: 380,
			height: 676,
			borderRadius: 28,
			overflow: 'hidden',
			border: `3px solid ${C.paper}cc`,
			boxShadow: '0 12px 50px rgba(0,0,0,0.7)',
		}}
	>
		<OffthreadVideo src={staticFile(src)} style={{width: '100%', height: '100%', objectFit: 'cover'}} />
	</div>
);

/** A clip in a square window, centred on `c.square` of the source width, over a blurred copy. */
const SquareShot: React.FC<{c: Clip; dur: number; filter?: string}> = ({c, dur, filter}) => {
	const {width} = useVideoConfig();
	const aspect = c.aspect ?? 16 / 9;
	// object-position places the crop window's edge, not its centre: convert.
	const pos = Math.min(1, Math.max(0, (c.square! * aspect - 0.5) / (aspect - 1)));
	return (
		<AbsoluteFill>
			<Shot def={c} dur={dur} filter={`blur(30px) brightness(0.32) ${filter ?? ''}`} />
			<div
				style={{
					position: 'absolute',
					left: 0,
					top: SQUARE_TOP,
					width,
					height: width,
					overflow: 'hidden',
					boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
				}}
			>
				<Shot def={{...c, focus: [pos, c.focus?.[1] ?? 0.5]}} dur={dur} box={{w: width, h: width}} zoom={c.pzoom ?? [1.0, 1.04]} filter={filter} />
			</div>
		</AbsoluteFill>
	);
};

/** One vertical short: gameplay clips back to back, timed captions, optional VO/presenter, end card. */
export const Short: React.FC<{def: ShortDef}> = ({def}) => {
	const {durationInFrames} = useVideoConfig();
	const endF = s(def.end ?? END_DEFAULT);
	const playF = durationInFrames - endF;
	let cursor = 0;
	const clips = def.clips.map((c, i) => {
		const from = cursor;
		const dur = clipFrames(c);
		cursor += dur;
		const filter = c.hold ? 'brightness(0.55) saturate(0.7)' : undefined;
		const shot = c.square != null ? <SquareShot c={c} dur={dur} filter={filter} /> : <Shot def={c} dur={dur} filter={filter} />;
		return (
			<Sequence key={i} from={from} durationInFrames={dur}>
				{c.hold ? <Freeze frame={0}>{shot}</Freeze> : shot}
				{c.flash ? <Flash at={0} strength={c.flash} /> : null}
			</Sequence>
		);
	});
	const last = def.clips[def.clips.length - 1];
	const p = def.presenter;
	return (
		<AbsoluteFill style={{background: C.ink}}>
			{clips}
			{def.captions.map((c, i) => {
				const from = s(c.at);
				const until = c.out == null ? playF : Math.min(playF, s(c.out) + 24);
				return (
					<Sequence key={`c${i}`} from={from} durationInFrames={Math.max(1, until - from)}>
						<CaptionLayer c={c} dur={until - from} />
					</Sequence>
				);
			})}
			{p ? (
				<Sequence from={s(p.at ?? 0)} durationInFrames={playF - s(p.at ?? 0)}>
					{p.mode === 'box' ? <BoxPresenter src={p.src} /> : <KeyedPresenter src={p.src} />}
				</Sequence>
			) : null}
			{(def.flashes ?? []).map((at, i) => (
				<Sequence key={`f${i}`} from={s(at)} durationInFrames={30}>
					<Flash at={0} strength={0.7} />
				</Sequence>
			))}
			{def.vo ? <Audio src={staticFile(def.vo)} /> : null}
			{(def.sfx ?? []).map((x, i) => (
				<Sequence key={`s${i}`} from={s(x.at)} durationInFrames={x.dur ? s(x.dur) : undefined}>
					<Audio src={staticFile(x.src)} volume={x.volume ?? 1} />
				</Sequence>
			))}
			{endF > 0 ? (
				<Sequence from={playF} durationInFrames={endF}>
					<EndCard
						bgDef={{...last, from: last.hold ? last.from : last.to - 0.5}}
						dur={endF}
						speed={2.4}
						tag={def.endTag ?? 'STAY IN THE SHADOWS.'}
						badges
					/>
				</Sequence>
			) : null}
			<Grain />
		</AbsoluteFill>
	);
};
