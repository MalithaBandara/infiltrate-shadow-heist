import React from 'react';
import {AbsoluteFill, Easing, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {ShotId} from '../shots';
import {C, DISPLAY, TEXT_SHADOW} from '../theme';
import {Kinetic} from './Kinetic';
import {Shot} from './Shot';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/** Tiles hold this zoom so the full-screen shot after the push-in can start from it. */
export const TILE_ZOOM = 1.05;

export type Tile = {id: ShotId; caption: string};

/** 3x2 tiles across a landscape frame, 2x3 down a portrait one. */
function gridLayout(W: number, H: number) {
	const portrait = H > W;
	const cols = portrait ? 2 : 3;
	const rows = portrait ? 3 : 2;
	const gap = 24;
	const TW = portrait ? Math.floor((W - 2 * 40 - gap) / 2) : 560;
	const TH = Math.round((TW * 9) / 16);
	const GX = (W - (cols * TW + (cols - 1) * gap)) / 2;
	const gridH = rows * TH + (rows - 1) * gap;
	const GY = portrait ? (H - gridH) / 2 + 70 : (H - gridH) / 2;
	return {portrait, cols, gap, TW, TH, GX, GY};
}

/**
 * Six levels playing at once, then a push into `target` until that tile fills the frame - the
 * next scene continues the same shot full screen, so the cut is invisible.
 */
export const MissionGrid: React.FC<{tiles: Tile[]; target: number; zoomAt: number; zoomDur: number; dur: number; title: string}> = ({
	tiles,
	target,
	zoomAt,
	zoomDur,
	dur,
	title,
}) => {
	const frame = useCurrentFrame();
	const {width: W, height: H} = useVideoConfig();
	const {portrait, cols, gap, TW, TH, GX, GY} = gridLayout(W, H);
	const q = interpolate(frame, [zoomAt, zoomAt + zoomDur], [0, 1], {...clamp, easing: Easing.inOut(Easing.cubic)});
	const tx = GX + (target % cols) * (TW + gap);
	const ty = GY + Math.floor(target / cols) * (TH + gap);
	const cx = tx + TW / 2;
	const cy = ty + TH / 2;
	const S = Math.max(W / TW, H / TH);
	const scale = Math.pow(S, q);
	const titleOut = zoomAt - 14;
	return (
		<AbsoluteFill style={{background: C.ink}}>
			<AbsoluteFill
				style={{
					transformOrigin: `${cx}px ${cy}px`,
					transform: `translate(${(W / 2 - cx) * q}px, ${(H / 2 - cy) * q}px) scale(${scale})`,
				}}
			>
				{tiles.map((t, i) => {
					const x = GX + (i % cols) * (TW + gap);
					const y = GY + Math.floor(i / cols) * (TH + gap);
					const d = i * 4;
					const p = interpolate(frame, [d, d + 16], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
					const isTarget = i === target;
					const fade = isTarget ? 1 : 1 - interpolate(q, [0, 0.5], [0, 1], clamp);
					return (
						<div
							key={t.id}
							style={{
								position: 'absolute',
								left: x,
								top: y,
								width: TW,
								height: TH,
								overflow: 'hidden',
								opacity: fade,
								clipPath: `inset(${(1 - p) * 50}% 0 ${(1 - p) * 50}% 0)`,
								transform: `scale(${interpolate(p, [0, 1], [0.92, 1])})`,
								outline: `2px solid rgba(238,243,248,${0.22 * (1 - q)})`,
								outlineOffset: -2,
							}}
						>
							<Shot id={t.id} dur={dur} zoom={[TILE_ZOOM, TILE_ZOOM]} box={{w: TW, h: TH}} />
							<div
								style={{
									position: 'absolute',
									left: 16,
									bottom: 10,
									fontFamily: DISPLAY,
									fontSize: portrait ? 28 : 30,
									letterSpacing: '0.12em',
									color: C.paper,
									opacity: 0.9 * (1 - q),
									textShadow: TEXT_SHADOW,
								}}
							>
								{t.caption}
							</div>
						</div>
					);
				})}
			</AbsoluteFill>
			{portrait ? (
				<Kinetic
					lines={[{text: title, size: 190, accent: {word: title.split(' ')[0], color: C.torch}}]}
					at={12}
					out={titleOut}
					align="center"
					style={{left: 0, right: 0, top: GY - 250}}
				/>
			) : (
				<>
					<AbsoluteFill
						style={{
							background:
								'linear-gradient(180deg, rgba(4,6,10,0) 30%, rgba(4,6,10,0.72) 44%, rgba(4,6,10,0.72) 56%, rgba(4,6,10,0) 70%)',
							opacity: interpolate(frame, [6, 20, titleOut, titleOut + 10], [0, 1, 1, 0], clamp),
						}}
					/>
					<Kinetic
						lines={[{text: title, size: 230, accent: {word: title.split(' ')[0], color: C.torch}}]}
						at={12}
						out={titleOut}
						align="center"
						style={{left: 0, right: 0, top: H / 2 - 118}}
					/>
				</>
			)}
		</AbsoluteFill>
	);
};

const GADGETS = ['gadget_invis', 'gadget_lasershield', 'gadget_boots', 'gadget_checkpoints', 'gadget_checkpoint', 'gadget_jammer'];

/** The gadget icons burst out of the centre onto a ring around "GEAR UP." */
export const GadgetBurst: React.FC<{bg: ShotId; dur: number}> = ({bg, dur}) => {
	const frame = useCurrentFrame();
	const {fps, width: W, height: H} = useVideoConfig();
	const portrait = H > W;
	const spin = interpolate(frame, [0, dur], [-4, 4]);
	const [RX, RY, size] = portrait ? [330, 520, 230] : [640, 330, 250];
	return (
		<AbsoluteFill>
			<Shot id={bg} dur={dur} filter="blur(5px) brightness(0.42) saturate(0.85)" />
			<AbsoluteFill style={{background: 'radial-gradient(circle at 50% 50%, rgba(80,170,255,0.22), rgba(0,0,0,0) 55%)'}} />
			{GADGETS.map((g, i) => {
				// Portrait starts the ring at 0 degrees so no icon sits on the text's row.
				const a = ((-90 + spin + (360 / GADGETS.length) * (i + (portrait ? 0 : 0.5))) * Math.PI) / 180;
				const p = spring({frame: frame - 4 - i * 3, fps, config: {damping: 13, stiffness: 150, mass: 0.7}});
				const bob = Math.sin((frame + i * 17) / 14) * 6;
				return (
					<div
						key={g}
						style={{
							position: 'absolute',
							left: W / 2 + Math.cos(a) * RX * p - size / 2,
							top: H / 2 + Math.sin(a) * RY * p - size / 2 + bob,
							width: size,
							height: size,
							transform: `scale(${0.3 + 0.7 * p}) rotate(${(1 - p) * -40}deg)`,
							opacity: Math.min(1, p * 2),
						}}
					>
						<div
							style={{
								position: 'absolute',
								inset: -30,
								background: 'radial-gradient(circle, rgba(90,190,255,0.35), rgba(0,0,0,0) 62%)',
							}}
						/>
						<Img
							src={staticFile(`img/${g}.png`)}
							style={{position: 'absolute', inset: 0, width: '100%', height: '100%', filter: 'drop-shadow(0 16px 26px rgba(0,0,0,0.6))'}}
						/>
					</div>
				);
			})}
			<Kinetic
				lines={[{text: 'GEAR UP.', size: portrait ? 190 : 220}]}
				at={10}
				align="center"
				style={{left: 0, right: 0, top: H / 2 - (portrait ? 92 : 108)}}
			/>
		</AbsoluteFill>
	);
};

/** Logo lit by a sweeping torch, then the tagline and the call to action. */
export const EndCard: React.FC<{bg: ShotId; dur: number}> = ({bg, dur}) => {
	const frame = useCurrentFrame();
	const {fps, width: W, height: H} = useVideoConfig();
	const portrait = H > W;
	const logoW = portrait ? 920 : 1150;
	const logoH = Math.round((logoW * 724) / 2172);
	const logoTop = portrait ? 620 : 205;
	const sweep = interpolate(frame, [0, 34], [-15, 112], {...clamp, easing: Easing.inOut(Easing.quad)});
	const open = interpolate(frame, [24, 50], [0, 1], {...clamp, easing: Easing.inOut(Easing.cubic)});
	const r = 360 + open * 2600;
	const mask = open >= 1 ? undefined : `radial-gradient(circle ${r}px at ${sweep}% 50%, #000 55%, transparent 100%)`;
	const glare = interpolate(frame, [0, 10, 34, 50], [0, 0.55, 0.4, 0], clamp);
	const bgIn = interpolate(frame, [0, 40], [0, 1], clamp);
	const cta = spring({frame: frame - 66, fps, config: {damping: 18, stiffness: 140}});
	const logoScale = interpolate(frame, [0, dur], [1.04, 1]);
	const tagTop = logoTop + logoH + (portrait ? 70 : 52);
	return (
		<AbsoluteFill style={{background: '#000'}}>
			<AbsoluteFill style={{opacity: bgIn}}>
				<Shot id={bg} dur={dur} filter="blur(7px) brightness(0.3) saturate(0.8)" />
			</AbsoluteFill>
			<div
				style={{
					position: 'absolute',
					left: (W - logoW) / 2,
					top: logoTop,
					width: logoW,
					height: logoH,
					transform: `scale(${logoScale})`,
					WebkitMaskImage: mask,
					maskImage: mask,
				}}
			>
				<Img src={staticFile('img/logo_main.png')} style={{width: '100%', height: '100%', filter: 'drop-shadow(0 10px 40px rgba(0,0,0,0.8))'}} />
			</div>
			<div
				style={{
					position: 'absolute',
					left: `calc(${sweep}% - 500px)`,
					top: logoTop + logoH / 2 - 500,
					width: 1000,
					height: 1000,
					background: 'radial-gradient(circle, rgba(255,244,214,0.5), rgba(255,244,214,0) 60%)',
					mixBlendMode: 'screen',
					opacity: glare,
				}}
			/>
			<Kinetic
				lines={[{text: 'STAY IN THE SHADOWS.', size: portrait ? 84 : 92, color: C.paper}]}
				at={44}
				stagger={3}
				align="center"
				style={{left: 0, right: 0, top: tagTop}}
			/>
			<div
				style={{
					position: 'absolute',
					left: 0,
					right: 0,
					top: tagTop + (portrait ? 150 : 150),
					display: 'flex',
					justifyContent: 'center',
					opacity: cta,
					transform: `translateY(${(1 - cta) * 24}px)`,
				}}
			>
				{/* A descriptor, not a button: Play's preview-asset rules bar price text ("free") and
				    calls to action in the video as well as the screenshots. */}
				<div
					style={{
						fontFamily: DISPLAY,
						fontSize: 40,
						letterSpacing: '0.22em',
						color: C.torch,
						border: `2px solid ${C.torch}88`,
						padding: '12px 26px 6px 34px',
						borderRadius: 4,
					}}
				>
					A SILHOUETTE STEALTH HEIST
				</div>
			</div>
		</AbsoluteFill>
	);
};
