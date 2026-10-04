import React from 'react';
import {Easing, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, DISPLAY, TEXT_SHADOW} from '../theme';

export type Line = {text: string; size?: number; color?: string; accent?: {word: string; color: string}};

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/** A word that rises out of its own mask, tracking in as it lands, and leaves upward. */
const Word: React.FC<{text: string; delay: number; exitAt?: number; color: string; glow?: string}> = ({
	text,
	delay,
	exitAt,
	color,
	glow,
}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const p = spring({frame: frame - delay, fps, config: {damping: 20, stiffness: 170, mass: 0.7}});
	const e = exitAt == null ? 0 : interpolate(frame, [exitAt, exitAt + 12], [0, 1], {...clamp, easing: Easing.in(Easing.cubic)});
	const y = (1 - p) * 105 - e * 105;
	const track = interpolate(p, [0, 1], [0.14, 0.035]);
	return (
		<span style={{display: 'inline-block', overflow: 'hidden', verticalAlign: 'top', padding: '0.06em 0.02em 0'}}>
			<span
				style={{
					display: 'inline-block',
					transform: `translateY(${y}%)`,
					letterSpacing: `${track}em`,
					color,
					textShadow: glow ? `0 0 28px ${glow}, ${TEXT_SHADOW}` : TEXT_SHADOW,
				}}
			>
				{text}
			</span>
		</span>
	);
};

/**
 * Stacked display lines revealed word by word. `at` is the first word's start frame, `stagger`
 * the gap between words (continuing across lines), `out` the frame the whole block exits.
 */
export const Kinetic: React.FC<{
	lines: Line[];
	at?: number;
	out?: number;
	stagger?: number;
	lineGap?: number;
	align?: 'left' | 'center' | 'right';
	style?: React.CSSProperties;
	rule?: boolean;
}> = ({lines, at = 0, out, stagger = 4, lineGap = 10, align = 'left', style, rule = false}) => {
	const frame = useCurrentFrame();
	let i = 0;
	const ruleP = interpolate(frame, [at + 6, at + 30], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
	const ruleOut = out == null ? 0 : interpolate(frame, [out, out + 10], [0, 1], clamp);
	return (
		<div style={{position: 'absolute', fontFamily: DISPLAY, lineHeight: 0.92, textAlign: align, ...style}}>
			{lines.map((line, li) => (
				<div key={li} style={{fontSize: line.size ?? 150, marginTop: li === 0 ? 0 : lineGap}}>
					{line.text.split(' ').map((w, wi) => {
						const d = at + i * stagger;
						const ex = out == null ? undefined : out + i * 2;
						i++;
						const isAccent = line.accent && w.replace(/[^\w']/g, '') === line.accent.word;
						return (
							<React.Fragment key={wi}>
								{wi > 0 ? ' ' : null}
								<Word
									text={w}
									delay={d}
									exitAt={ex}
									color={isAccent ? line.accent!.color : line.color ?? C.paper}
									glow={isAccent ? line.accent!.color : undefined}
								/>
							</React.Fragment>
						);
					})}
				</div>
			))}
			{rule ? (
				<div
					style={{
						height: 4,
						marginTop: 18,
						width: 220,
						background: C.paper,
						transformOrigin: align === 'right' ? 'right' : align === 'center' ? 'center' : 'left',
						transform: `scaleX(${ruleP * (1 - ruleOut)})`,
						marginLeft: align === 'center' ? 'auto' : align === 'right' ? 'auto' : 0,
						marginRight: align === 'center' ? 'auto' : 0,
						boxShadow: '0 2px 12px rgba(0,0,0,0.5)',
					}}
				/>
			) : null}
		</div>
	);
};
