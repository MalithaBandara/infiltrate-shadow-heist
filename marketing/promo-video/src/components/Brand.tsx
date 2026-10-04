import React from 'react';
import {AbsoluteFill, Img, interpolate, random, spring, staticFile, useCurrentFrame, useVideoConfig, Easing} from 'remotion';
import {COLORS, MONO_FONT, TITLE_FONT} from '../theme';

/** The game's wordmark slamming in with an RGB-split shake that settles. */
export const LogoSting: React.FC<{width?: number; y?: number; tagline?: string; taglineAt?: number}> = ({width = 1180, y = 400, tagline, taglineAt = 26}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const pop = spring({frame, fps, config: {damping: 12, stiffness: 160, mass: 0.7}});
	const scale = interpolate(pop, [0, 1], [1.35, 1]);
	const opacity = interpolate(frame, [0, 4], [0, 1], {extrapolateRight: 'clamp'});
	const shake = Math.max(0, 1 - frame / 18);
	const jx = (random(`lx${frame}`) - 0.5) * 26 * shake;
	const jy = (random(`ly${frame}`) - 0.5) * 12 * shake;
	const split = 14 * shake;
	const tag = interpolate(frame - taglineAt, [0, 14], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.out(Easing.cubic)});
	const logo = staticFile('brand/logo_main.png');
	const h = (width * 724) / 2172;
	const common: React.CSSProperties = {position: 'absolute', width, height: h, left: (1920 - width) / 2, top: y - h / 2};
	return (
		<AbsoluteFill style={{opacity}}>
			<div style={{transform: `translate(${jx}px, ${jy}px) scale(${scale})`, transformOrigin: `50% ${y}px`, position: 'absolute', inset: 0}}>
				{split > 0.5 ? (
					<>
						<Img src={logo} style={{...common, transform: `translateX(${-split}px)`, opacity: 0.6, filter: 'drop-shadow(0 0 0 #f00) sepia(1) saturate(8) hue-rotate(-50deg)', mixBlendMode: 'screen'}} />
						<Img src={logo} style={{...common, transform: `translateX(${split}px)`, opacity: 0.6, filter: 'sepia(1) saturate(8) hue-rotate(160deg)', mixBlendMode: 'screen'}} />
					</>
				) : null}
				<Img src={logo} style={{...common, filter: 'drop-shadow(0 10px 50px rgba(0,0,0,0.8))'}} />
			</div>
			{tagline ? (
				<div
					style={{
						position: 'absolute',
						top: y + h / 2 + 30,
						left: 0,
						right: 0,
						textAlign: 'center',
						fontFamily: MONO_FONT,
						fontSize: 42,
						letterSpacing: interpolate(tag, [0, 1], [30, 14]),
						color: COLORS.steel,
						opacity: tag,
						textShadow: '0 2px 20px rgba(0,0,0,0.9)',
					}}
				>
					{tagline}
				</div>
			) : null}
		</AbsoluteFill>
	);
};

/** The five gadgets the in-game store sells, with its own icons and names (StoreScreen.kt / Powerup.kt). */
const GADGETS: {file: string; name: string}[] = [
	{file: 'gadget_invis.png', name: 'INVISIBILITY CLOAK'},
	{file: 'gadget_boots.png', name: 'STEALTH BOOTS'},
	{file: 'gadget_lasershield.png', name: 'GUARD SHIELD'},
	{file: 'gadget_checkpoint.png', name: 'REMOTE TRIGGER'},
	{file: 'gadget_checkpoints.png', name: 'CHECKPOINTS'},
];

/** The gadget icons dealt out in a row, each with its name, then held. */
export const GadgetRow: React.FC<{y?: number; size?: number; gap?: number; stagger?: number}> = ({y = 560, size = 200, gap = 64, stagger = 5}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const total = GADGETS.length * size + (GADGETS.length - 1) * gap;
	const left0 = (1920 - total) / 2;
	return (
		<AbsoluteFill>
			{GADGETS.map((g, i) => {
				const p = spring({frame: frame - i * stagger, fps, config: {damping: 11, stiffness: 170, mass: 0.6}});
				const label = interpolate(frame - i * stagger - 8, [0, 10], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
				return (
					<div key={g.file} style={{position: 'absolute', left: left0 + i * (size + gap), top: y - size / 2, width: size, textAlign: 'center'}}>
						<div
							style={{
								width: size,
								height: size,
								borderRadius: 28,
								background: 'rgba(8,12,18,0.72)',
								border: `2px solid rgba(143,180,214,${0.25 + 0.35 * label})`,
								boxShadow: `0 0 ${40 * label}px rgba(255,179,71,0.25)`,
								transform: `translateY(${(1 - p) * 120}px) scale(${0.6 + 0.4 * p}) rotate(${(1 - p) * (i % 2 ? 12 : -12)}deg)`,
								opacity: Math.min(1, p * 1.5),
								display: 'flex',
								alignItems: 'center',
								justifyContent: 'center',
							}}
						>
							<Img src={staticFile(`brand/${g.file}`)} style={{width: size * 0.78, height: size * 0.78, objectFit: 'contain'}} />
						</div>
						<div style={{marginTop: 18, fontFamily: MONO_FONT, fontSize: 26, lineHeight: 1.3, letterSpacing: 4, color: COLORS.white, opacity: label, marginLeft: -30, marginRight: -30}}>
							{g.name}
						</div>
					</div>
				);
			})}
		</AbsoluteFill>
	);
};

/** Big number that counts up with a slot-machine flicker, e.g. 1 -> 12. */
export const CountUp: React.FC<{to: number; label: string; x?: number; y?: number; frames?: number}> = ({to, label, x = 120, y = 300, frames = 30}) => {
	const frame = useCurrentFrame();
	const v = Math.max(1, Math.round(interpolate(frame, [0, frames], [1, to], {extrapolateRight: 'clamp', easing: Easing.out(Easing.quad)})));
	const o = interpolate(frame, [0, 6], [0, 1], {extrapolateRight: 'clamp'});
	const settle = spring({frame: frame - frames, fps: 60, config: {damping: 10, stiffness: 200}});
	return (
		<div style={{position: 'absolute', left: x, top: y, opacity: o, display: 'flex', alignItems: 'baseline', gap: 28}}>
			<div style={{fontFamily: TITLE_FONT, fontSize: 300, lineHeight: 0.9, color: COLORS.amber, textShadow: '0 0 60px rgba(255,179,71,0.45)', transform: `scale(${1 + 0.08 * (1 - settle) * (frame >= frames ? 1 : 0)})`, transformOrigin: 'left bottom', width: 330}}>
				{String(v).padStart(2, '0')}
			</div>
			<div style={{fontFamily: TITLE_FONT, fontSize: 150, lineHeight: 0.9, color: COLORS.white, letterSpacing: 6, textShadow: '0 6px 40px rgba(0,0,0,0.7)'}}>{label}</div>
		</div>
	);
};
