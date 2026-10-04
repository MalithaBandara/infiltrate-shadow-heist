import React from 'react';
import {AbsoluteFill, Easing, Img, interpolate, random, spring, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, DISPLAY, TEXT_SHADOW} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/** Animated film grain + edge vignette laid over the whole video. */
export const Grain: React.FC<{opacity?: number}> = ({opacity = 0.075}) => {
	const frame = useCurrentFrame();
	const step = Math.floor(frame / 2);
	const k = step % 8;
	const ox = Math.floor(random(`gx${step}`) * 512);
	const oy = Math.floor(random(`gy${step}`) * 512);
	return (
		<AbsoluteFill style={{pointerEvents: 'none'}}>
			<AbsoluteFill
				style={{
					backgroundImage: `url(${staticFile(`img/grain_${k}.png`)})`,
					backgroundPosition: `${ox}px ${oy}px`,
					mixBlendMode: 'overlay',
					opacity,
				}}
			/>
			<AbsoluteFill
				style={{background: 'radial-gradient(ellipse 75% 70% at 50% 50%, rgba(0,0,0,0) 55%, rgba(0,0,0,0.55) 100%)'}}
			/>
		</AbsoluteFill>
	);
};

/** Cinema bars: slam in at `inAt`, retract from `outAt`. */
export const Letterbox: React.FC<{inAt?: number; outAt: number; size?: number}> = ({inAt = -100, outAt, size = 132}) => {
	const frame = useCurrentFrame();
	const pin = interpolate(frame, [inAt, inAt + 10], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
	const pout = interpolate(frame, [outAt, outAt + 16], [0, 1], {...clamp, easing: Easing.inOut(Easing.cubic)});
	const h = size * pin * (1 - pout);
	const bar: React.CSSProperties = {position: 'absolute', left: 0, right: 0, height: h, background: '#000'};
	return (
		<AbsoluteFill style={{pointerEvents: 'none'}}>
			<div style={{...bar, top: 0}} />
			<div style={{...bar, bottom: 0}} />
		</AbsoluteFill>
	);
};

/** White exposure flash with a second, weaker flicker - lightning. */
export const Flash: React.FC<{at: number; color?: string; strength?: number}> = ({at, color = '#fff', strength = 1}) => {
	const frame = useCurrentFrame();
	const f = frame - at;
	const o = interpolate(f, [0, 2, 9, 11, 13, 26], [0, 1, 0.08, 0.6, 0.35, 0], clamp) * strength;
	if (o <= 0) return null;
	return <AbsoluteFill style={{background: color, opacity: o, mixBlendMode: 'screen', pointerEvents: 'none'}} />;
};

const deg = (d: number) => (d * Math.PI) / 180;

function conePolygon(apex: [number, number], a1: number, a2: number, r = 6000): string {
	const pts: string[] = [`${apex[0]}px ${apex[1]}px`];
	const n = 8;
	for (let i = 0; i <= n; i++) {
		const a = deg(a1 + ((a2 - a1) * i) / n);
		pts.push(`${apex[0] + Math.cos(a) * r}px ${apex[1] + Math.sin(a) * r}px`);
	}
	return `polygon(${pts.join(',')})`;
}

/**
 * Torch-beam transition: the incoming shot (children) is seen only inside a light cone that
 * swings in from a lamp above the top-left corner and opens until it covers the frame.
 */
export const BeamReveal: React.FC<{dur: number; children: React.ReactNode}> = ({dur, children}) => {
	const frame = useCurrentFrame();
	const {width: W, height: H} = useVideoConfig();
	const p = interpolate(frame, [0, dur], [0, 1], {...clamp, easing: Easing.inOut(Easing.cubic)});
	const apex: [number, number] = [-220, -320];
	const portrait = H > W;
	const centre = interpolate(p, [0, 1], portrait ? [80, 50] : [74, 45]);
	const half = interpolate(p, [0, 0.35, 1], [1.5, 9, 58]);
	const glow = interpolate(p, [0, 0.25, 0.8, 1], [0, 0.55, 0.3, 0]);
	const svgPts = (a1: number, a2: number) => {
		const pts: string[] = [`${apex[0]},${apex[1]}`];
		for (let i = 0; i <= 8; i++) {
			const a = deg(a1 + ((a2 - a1) * i) / 8);
			pts.push(`${apex[0] + Math.cos(a) * 6000},${apex[1] + Math.sin(a) * 6000}`);
		}
		return pts.join(' ');
	};
	return (
		<AbsoluteFill>
			<AbsoluteFill style={{clipPath: p >= 1 ? undefined : conePolygon(apex, centre - half, centre + half)}}>{children}</AbsoluteFill>
			{glow > 0 ? (
				<svg width={W} height={H} style={{position: 'absolute', inset: 0, mixBlendMode: 'screen', opacity: glow}}>
					<defs>
						<radialGradient id="beamg" cx={apex[0]} cy={apex[1]} r={2600} gradientUnits="userSpaceOnUse">
							<stop offset="0" stopColor="#FFF4D6" stopOpacity="1" />
							<stop offset="1" stopColor="#FFF4D6" stopOpacity="0" />
						</radialGradient>
						<filter id="beamblur" x="-50%" y="-50%" width="200%" height="200%">
							<feGaussianBlur stdDeviation="26" />
						</filter>
					</defs>
					<polygon points={svgPts(centre - half - 2, centre + half + 2)} fill="url(#beamg)" filter="url(#beamblur)" />
				</svg>
			) : null}
		</AbsoluteFill>
	);
};

/**
 * Laser-scan transition: a red beam sweeps left to right across a steep diagonal, and the
 * incoming shot (children) is revealed behind it.
 */
export const LaserReveal: React.FC<{dur: number; children: React.ReactNode}> = ({dur, children}) => {
	const frame = useCurrentFrame();
	const {width: W, height: H} = useVideoConfig();
	const p = interpolate(frame, [0, dur], [0, 1], {...clamp, easing: Easing.inOut(Easing.quad)});
	const k = 260; // horizontal lean of the line across the frame height
	const X = interpolate(p, [0, 1], [-k - 40, W + k + 40]);
	const clip = `polygon(-10px -10px, ${X + k}px -10px, ${X - k}px ${H + 10}px, -10px ${H + 10}px)`;
	const len = Math.hypot(2 * k, H) + 200;
	const angle = (Math.atan2(H, -2 * k) * 180) / Math.PI;
	const on = p > 0 && p < 1;
	return (
		<AbsoluteFill>
			<AbsoluteFill style={{clipPath: p >= 1 ? undefined : clip}}>{children}</AbsoluteFill>
			{on ? (
				<div
					style={{
						position: 'absolute',
						left: X - len / 2,
						top: H / 2 - 3,
						width: len,
						height: 6,
						transform: `rotate(${angle}deg)`,
						background: 'linear-gradient(90deg, rgba(255,60,60,0), #ffd0d0 12%, #ffffff 50%, #ffd0d0 88%, rgba(255,60,60,0))',
						boxShadow: `0 0 18px 6px ${C.alert}, 0 0 60px 18px rgba(255,40,40,0.55)`,
						borderRadius: 3,
					}}
				/>
			) : null}
		</AbsoluteFill>
	);
};

/**
 * Level 11's roll-up door as a transition: a slatted steel panel slams down over the outgoing
 * shot, holds, then rolls up to reveal whatever is underneath. `closeAt` is when it is shut.
 */
export const DoorWipe: React.FC<{closeAt: number; drop?: number; hold?: number; lift?: number}> = ({
	closeAt,
	drop = 9,
	hold = 4,
	lift = 22,
}) => {
	const frame = useCurrentFrame();
	const {width: W, height: H} = useVideoConfig();
	const down = interpolate(frame, [closeAt - drop, closeAt], [0, 1], {...clamp, easing: Easing.in(Easing.cubic)});
	const up = interpolate(frame, [closeAt + hold, closeAt + hold + lift], [0, 1], {...clamp, easing: Easing.inOut(Easing.cubic)});
	const cover = down * (1 - up);
	if (cover <= 0) return null;
	// Small bounce as it lands.
	const bounce = frame >= closeAt && frame < closeAt + hold ? Math.sin(((frame - closeAt) / hold) * Math.PI) * 10 : 0;
	const y = -H * (1 - cover) - bounce;
	return (
		<AbsoluteFill style={{pointerEvents: 'none'}}>
			<div
				style={{
					position: 'absolute',
					left: 0,
					top: y,
					width: W,
					height: H,
					background:
						'repeating-linear-gradient(180deg, #2a333c 0px, #36404a 6px, #252d35 30px, #11161b 36px)',
					boxShadow: '0 30px 60px rgba(0,0,0,0.75)',
				}}
			>
				<div
					style={{
						position: 'absolute',
						left: 0,
						right: 0,
						bottom: 0,
						height: 34,
						background: 'repeating-linear-gradient(135deg, #E8B51F 0 26px, #111 26px 52px)',
						borderTop: '6px solid #0b0e11',
					}}
				/>
			</div>
		</AbsoluteFill>
	);
};

/** Montage label: the verb for the shot, slammed in bottom-left with its count. */
export const Label: React.FC<{word: string; index: number; total: number; left?: number; bottom?: number; top?: number; size?: number}> = ({
	word,
	index,
	total,
	left = 120,
	bottom = 118,
	top,
	size = 190,
}) => {
	const frame = useCurrentFrame();
	const p = interpolate(frame, [0, 8], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
	const scale = interpolate(p, [0, 1], [1.3, 1]);
	const blur = interpolate(p, [0, 1], [10, 0]);
	const bar = interpolate(frame, [3, 16], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
	return (
		<div style={{position: 'absolute', left, ...(top == null ? {bottom} : {top}), fontFamily: DISPLAY}}>
			<div style={{fontSize: 34, color: C.cold, letterSpacing: '0.18em', opacity: p, textShadow: TEXT_SHADOW}}>
				{String(index).padStart(2, '0')} / {String(total).padStart(2, '0')}
			</div>
			<div
				style={{
					fontSize: size,
					lineHeight: 0.9,
					color: C.paper,
					letterSpacing: '0.04em',
					transform: `scale(${scale})`,
					transformOrigin: 'left bottom',
					filter: `blur(${blur}px)`,
					opacity: p,
					textShadow: TEXT_SHADOW,
				}}
			>
				{word}
			</div>
			<div style={{height: 6, width: 140, background: C.torch, transform: `scaleX(${bar})`, transformOrigin: 'left', marginTop: 10}} />
		</div>
	);
};

/** Red detection pulse at the frame edges. */
export const AlertPulse: React.FC<{at: number; pulses?: number; period?: number}> = ({at, pulses = 3, period = 34}) => {
	const frame = useCurrentFrame();
	const f = frame - at;
	if (f < 0 || f > pulses * period) return null;
	const phase = (f % period) / period;
	const o = Math.pow(Math.sin(phase * Math.PI), 1.6) * 0.95;
	return (
		<AbsoluteFill
			style={{
				pointerEvents: 'none',
				opacity: o,
				background: 'radial-gradient(ellipse 70% 62% at 50% 50%, rgba(255,0,0,0) 45%, rgba(255,20,20,0.55) 85%, rgba(170,0,0,0.85) 100%)',
				mixBlendMode: 'screen',
			}}
		/>
	);
};

/** Big display text with RGB-split glitch jitter on a few frames. */
export const GlitchText: React.FC<{text: string; at: number; out?: number; size?: number; style?: React.CSSProperties}> = ({
	text,
	at,
	out,
	size = 200,
	style,
}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const f = frame - at;
	if (f < 0) return null;
	const p = spring({frame: f, fps, config: {damping: 14, stiffness: 220, mass: 0.6}});
	const o = out == null ? 1 : interpolate(frame, [out, out + 8], [1, 0], clamp);
	const step = Math.floor(frame / 3);
	const glitching = f < 10 || random(`gl${step}`) > 0.82;
	const jx = glitching ? (random(`jx${step}`) - 0.5) * 26 : 0;
	const split = glitching ? 8 + random(`sp${step}`) * 10 : 2;
	const common: React.CSSProperties = {
		position: 'absolute',
		inset: 0,
		fontFamily: DISPLAY,
		fontSize: size,
		lineHeight: 0.95,
		letterSpacing: '0.04em',
		whiteSpace: 'pre',
	};
	return (
		<div style={{position: 'absolute', transform: `translateX(${jx}px) scale(${interpolate(p, [0, 1], [1.25, 1])})`, opacity: o * Math.min(1, p * 1.5), ...style}}>
			<div style={{...common, position: 'relative', visibility: 'hidden'}}>{text}</div>
			<div style={{...common, color: '#ff2a2a', transform: `translateX(${-split}px)`, mixBlendMode: 'screen', opacity: 0.9}}>{text}</div>
			<div style={{...common, color: '#22e5ff', transform: `translateX(${split}px)`, mixBlendMode: 'screen', opacity: 0.75}}>{text}</div>
			<div style={{...common, color: C.paper, textShadow: TEXT_SHADOW}}>{text}</div>
		</div>
	);
};

/** Mission dossier card: paper slides in, handwritten mission number, stamped level name. */
export const Dossier: React.FC<{mission: string; name: string; out?: number; left?: number; top?: number}> = ({
	mission,
	name,
	out,
	left = 110,
	top = 150,
}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const p = spring({frame, fps, config: {damping: 16, stiffness: 120, mass: 0.8}});
	const o = out == null ? 0 : interpolate(frame, [out, out + 12], [0, 1], {...clamp, easing: Easing.in(Easing.cubic)});
	const stampF = frame - 14;
	const sp = interpolate(stampF, [0, 5], [0, 1], {...clamp, easing: Easing.in(Easing.quad)});
	const stampScale = interpolate(sp, [0, 1], [2.1, 1]);
	const shake = stampF >= 5 && stampF < 11 ? Math.sin(stampF * 3) * (11 - stampF) * 0.6 : 0;
	return (
		<div
			style={{
				position: 'absolute',
				left,
				top,
				width: 560,
				height: 373,
				transform: `translate(${interpolate(p, [0, 1], [-760, 0]) - o * 760}px, ${shake}px) rotate(${interpolate(p, [0, 1], [-12, -3])}deg)`,
				filter: 'drop-shadow(0 24px 40px rgba(0,0,0,0.7))',
			}}
		>
			<Img src={staticFile('img/dossier_paper.png')} style={{width: '100%', height: '100%'}} />
			<div
				style={{
					position: 'absolute',
					left: 70,
					top: 88,
					fontFamily: 'InfHand',
					fontSize: 64,
					color: '#1d2632',
					transform: 'rotate(-2deg)',
				}}
			>
				{mission}
			</div>
			<div
				style={{
					position: 'absolute',
					left: 64,
					top: 196,
					padding: '6px 22px 0',
					border: `6px solid ${C.stamp}`,
					borderRadius: 6,
					fontFamily: DISPLAY,
					fontSize: 72,
					letterSpacing: '0.06em',
					color: C.stamp,
					opacity: sp * 0.88,
					transform: `rotate(-7deg) scale(${stampScale})`,
					transformOrigin: 'center',
					mixBlendMode: 'multiply',
				}}
			>
				{name}
			</div>
		</div>
	);
};
