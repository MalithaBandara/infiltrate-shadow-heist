import React from 'react';
import {interpolate, spring, useCurrentFrame, useVideoConfig, Easing} from 'remotion';
import {COLORS, MONO_FONT, TITLE_FONT} from '../theme';

type Align = 'left' | 'center' | 'right';

/**
 * Big condensed title: letters rise out of a mask one after another, hold, then the whole line
 * slides out sideways. Timing is relative to the enclosing Sequence.
 */
export const KineticTitle: React.FC<{
	text: string;
	size?: number;
	color?: string;
	accent?: string;
	x?: number;
	y?: number;
	align?: Align;
	stagger?: number;
	/** Frame (in this Sequence) at which the exit starts; omit to hold. */
	exitAt?: number;
	tracking?: number;
	underline?: boolean;
}> = ({text, size = 220, color = COLORS.white, accent = COLORS.alarm, x = 120, y = 760, align = 'left', stagger = 2, exitAt, tracking = 4, underline = true}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const exit = exitAt === undefined ? 0 : interpolate(frame, [exitAt, exitAt + 10], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.in(Easing.cubic)});
	const bar = spring({frame: frame - 4, fps, config: {damping: 200}, durationInFrames: 18});
	const chars = text.split('');
	const anchor = align === 'left' ? {left: x} : align === 'right' ? {right: x} : {left: 0, right: 0};
	return (
		<div
			style={{
				position: 'absolute',
				top: y - size,
				...anchor,
				textAlign: align,
				transform: `translateX(${exit * -140}px)`,
				opacity: 1 - exit,
			}}
		>
			<div style={{display: 'inline-block'}}>
			<div style={{lineHeight: 1, paddingTop: size * 0.08, display: 'inline-block'}}>
				{chars.map((c, i) => {
					const p = spring({frame: frame - i * stagger, fps, config: {damping: 16, stiffness: 180, mass: 0.6}});
					return (
						<span
							key={i}
							style={{
								display: 'inline-block',
								fontFamily: TITLE_FONT,
								fontSize: size,
								letterSpacing: tracking,
								color,
								transform: `translateY(${(1 - p) * size * 0.5}px)`,
								opacity: Math.min(1, Math.max(0, p * 1.4)),
								textShadow: '0 6px 40px rgba(0,0,0,0.65)',
								whiteSpace: 'pre',
							}}
						>
							{c}
						</span>
					);
				})}
			</div>
			{underline ? (
				<div
					style={{
						height: Math.max(6, size * 0.035),
						width: `${bar * 100}%`,
						background: accent,
						marginTop: size * 0.02,
						marginLeft: align === 'right' ? 'auto' : align === 'center' ? 'auto' : 0,
						marginRight: align === 'center' ? 'auto' : 0,
						boxShadow: `0 0 24px ${accent}`,
					}}
				/>
			) : null}
			</div>
		</div>
	);
};

/** Small mono caption that types itself in, framed like a HUD readout: "[ 07 ] SERVICE TUNNEL". */
export const LevelTag: React.FC<{num: string; name: string; x?: number; y?: number; color?: string}> = ({num, name, x = 120, y = 150, color = COLORS.white}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const full = name.toUpperCase();
	const shown = Math.floor(interpolate(frame, [3, 3 + full.length * 0.7], [0, full.length], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}));
	const box = spring({frame, fps, config: {damping: 200}, durationInFrames: 12});
	const cursorOn = Math.floor(frame / 8) % 2 === 0;
	return (
		<div style={{position: 'absolute', left: x, top: y, display: 'flex', alignItems: 'center', gap: 22, fontFamily: MONO_FONT}}>
			<div
				style={{
					fontSize: 34,
					color: COLORS.ink,
					background: COLORS.amber,
					padding: '6px 14px',
					transform: `scaleX(${box})`,
					transformOrigin: 'left',
					letterSpacing: 2,
				}}
			>
				{num}
			</div>
			<div style={{fontSize: 40, color, letterSpacing: 6, textShadow: '0 2px 18px rgba(0,0,0,0.9)'}}>
				{full.slice(0, shown)}
				<span style={{opacity: cursorOn ? 1 : 0, color: COLORS.amber}}>_</span>
			</div>
		</div>
	);
};

/** One line of small caps copy that fades up. */
export const Kicker: React.FC<{text: string; x?: number; y?: number; align?: Align; size?: number; color?: string; delay?: number}> = ({text, x = 120, y = 820, align = 'left', size = 44, color = COLORS.steel, delay = 0}) => {
	const frame = useCurrentFrame();
	const o = interpolate(frame - delay, [0, 12], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const anchor = align === 'left' ? {left: x} : align === 'right' ? {right: x} : {left: 0, right: 0};
	return (
		<div
			style={{
				position: 'absolute',
				top: y,
				...anchor,
				textAlign: align,
				fontFamily: MONO_FONT,
				fontSize: size,
				letterSpacing: 8,
				color,
				opacity: o,
				transform: `translateY(${(1 - o) * 16}px)`,
				textShadow: '0 2px 18px rgba(0,0,0,0.9)',
			}}
		>
			{text}
		</div>
	);
};
