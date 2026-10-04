import React from 'react';
import {AbsoluteFill, Img, staticFile, useVideoConfig} from 'remotion';
import {FormatId, GADGETS, Shot, TILES} from './data';
import {DISPLAY, TEXT} from './fonts';

const INK = '#03060B';
const AMBER = '#FFC65C';
const PAPER = '#F2F5F8';
const SHADOW = '0 4px 28px rgba(0,0,0,0.55), 0 2px 4px rgba(0,0,0,0.45)';

const useFrame = () => {
	const {width: W, height: H} = useVideoConfig();
	return {W, H, u: Math.min(W / 1920, H / 1080)};
};

/** A gameplay frame cropped to fill its box (cover) around a focus point, with optional zoom. */
const Gameplay: React.FC<{src: string; focus?: [number, number]; zoom?: number; filter?: string}> = ({
	src,
	focus = [0.5, 0.5],
	zoom = 1,
	filter,
}) => {
	const [fx, fy] = focus;
	return (
		<Img
			src={staticFile(src)}
			style={{
				position: 'absolute',
				inset: 0,
				width: '100%',
				height: '100%',
				objectFit: 'cover',
				objectPosition: `${fx * 100}% ${fy * 100}%`,
				transform: `scale(${zoom})`,
				transformOrigin: `${fx * 100}% ${fy * 100}%`,
				filter,
			}}
		/>
	);
};

const clean = (w: string) => w.replace(/[^\w#&']/g, '');

/** Tag, headline (accent word lit) and one supporting line. */
const Caption: React.FC<{shot: Shot; u: number; align: 'left' | 'right' | 'center'; titleSize?: number; maxWidth?: number}> = ({
	shot,
	u,
	align,
	titleSize = 128,
	maxWidth = 760,
}) => {
	const accent = shot.accentColor ?? AMBER;
	return (
		<div style={{textAlign: align, maxWidth: maxWidth * u}}>
			{shot.tag ? (
				<div
					style={{
						display: 'flex',
						alignItems: 'center',
						gap: 14 * u,
						justifyContent: align === 'right' ? 'flex-end' : align === 'center' ? 'center' : 'flex-start',
						fontFamily: TEXT,
						fontWeight: 700,
						fontSize: 24 * u,
						letterSpacing: '0.34em',
						color: AMBER,
						marginBottom: 14 * u,
						textShadow: SHADOW,
					}}
				>
					{align !== 'right' ? <span style={{width: 44 * u, height: 4 * u, background: AMBER, display: 'inline-block'}} /> : null}
					<span style={{marginRight: align === 'right' ? -0.34 * 24 * u : 0}}>{shot.tag}</span>
					{align === 'right' ? <span style={{width: 44 * u, height: 4 * u, background: AMBER, display: 'inline-block'}} /> : null}
				</div>
			) : null}
			{shot.title.map((line, i) => (
				<div
					key={i}
					style={{
						fontFamily: DISPLAY,
						fontSize: titleSize * u,
						lineHeight: 0.9,
						letterSpacing: '0.02em',
						color: PAPER,
						textShadow: SHADOW,
						whiteSpace: 'nowrap',
					}}
				>
					{line.split(' ').map((w, j) => (
						<React.Fragment key={j}>
							{j > 0 ? ' ' : null}
							<span
								style={
									clean(w) === shot.accent
										? {color: accent, textShadow: `0 0 ${30 * u}px ${accent}55, ${SHADOW}`}
										: undefined
								}
							>
								{w}
							</span>
						</React.Fragment>
					))}
				</div>
			))}
			<div
				style={{
					fontFamily: TEXT,
					fontWeight: 500,
					fontSize: 34 * u,
					lineHeight: 1.25,
					color: 'rgba(232,238,245,0.9)',
					marginTop: 18 * u,
					textShadow: SHADOW,
				}}
			>
				{shot.sub}
			</div>
		</div>
	);
};

/** Darkens the corner the caption sits in so it reads over any frame. */
const Scrim: React.FC<{side: 'left' | 'right'; vpos: 'top' | 'bottom'; strength?: number}> = ({side, vpos, strength = 1}) => (
	<>
		<AbsoluteFill
			style={{
				opacity: strength,
				background: `linear-gradient(${side === 'left' ? '90deg' : '270deg'}, rgba(3,6,11,0.78) 0%, rgba(3,6,11,0.5) 30%, rgba(3,6,11,0) 60%)`,
			}}
		/>
		<AbsoluteFill
			style={{
				opacity: strength,
				background: `linear-gradient(${vpos === 'top' ? '180deg' : '0deg'}, rgba(3,6,11,0.55) 0%, rgba(3,6,11,0) 50%)`,
			}}
		/>
	</>
);

/**
 * iPad layout: the 16:9 frame sits full-width along the bottom; the space above is the same frame
 * blurred and dimmed, faded into the panel, and the caption sits there.
 */
const StackStage: React.FC<{src: string; focus?: [number, number]; zoom?: number; children?: React.ReactNode}> = ({
	src,
	focus,
	zoom,
	children,
}) => {
	const {W, H} = useFrame();
	const ph = Math.round((W * 9) / 16);
	return (
		<>
			<Gameplay src={src} focus={[0.5, 0.2]} zoom={1.2} filter="blur(46px) brightness(0.42) saturate(0.9)" />
			<div
				style={{
					position: 'absolute',
					left: 0,
					top: H - ph,
					width: W,
					height: ph,
					overflow: 'hidden',
					WebkitMaskImage: 'linear-gradient(180deg, transparent 0%, transparent 9%, #000 25%)',
					maskImage: 'linear-gradient(180deg, transparent 0%, transparent 9%, #000 25%)',
				}}
			>
				<Gameplay src={src} focus={focus} zoom={zoom} />
				{children}
			</div>
		</>
	);
};

const Logo: React.FC<{width: number}> = ({width}) => (
	<Img
		src={staticFile('img/logo_main.png')}
		style={{width, height: (width * 724) / 2172, display: 'block', filter: 'drop-shadow(0 8px 30px rgba(0,0,0,0.7))'}}
	/>
);

/** Four slanted slices of different levels. `top`/`height` is the area they fill. */
const Collage: React.FC<{top: number; height: number}> = ({top, height}) => {
	const {W, u} = useFrame();
	const n = TILES.length;
	const k = 0.07 * W; // horizontal lean of each seam across the area height
	const gap = 10 * u;
	const seam = (i: number) => (i * W) / n;
	const imgW = Math.max(W, (height * 16) / 9) * 1.12;
	const imgH = (imgW * 9) / 16;
	return (
		<div style={{position: 'absolute', left: 0, top, width: W, height, overflow: 'hidden', background: INK}}>
			{TILES.map((t, i) => {
				const tl = i === 0 ? -k : seam(i) + k / 2;
				const tr = i === n - 1 ? W + k : seam(i + 1) + k / 2 - gap;
				const bl = i === 0 ? -k : seam(i) - k / 2;
				const br = i === n - 1 ? W + k : seam(i + 1) - k / 2 - gap;
				const cx = (tl + tr + bl + br) / 4;
				return (
					<div
						key={t.src}
						style={{
							position: 'absolute',
							inset: 0,
							clipPath: `polygon(${tl}px 0, ${tr}px 0, ${br}px ${height}px, ${bl}px ${height}px)`,
						}}
					>
						<Img
							src={staticFile(t.src)}
							style={{
								position: 'absolute',
								width: imgW,
								height: imgH,
								left: cx - t.x * imgW,
								top: height - imgH * 0.96,
							}}
						/>
						<AbsoluteFill style={{background: 'linear-gradient(0deg, rgba(3,6,11,0.75) 0%, rgba(3,6,11,0) 28%)'}} />
						<div
							style={{
								position: 'absolute',
								left: Math.max(bl, 0) + 34 * u,
								bottom: 30 * u,
								fontFamily: TEXT,
								fontWeight: 700,
								fontSize: 24 * u,
								letterSpacing: '0.2em',
								color: PAPER,
								textShadow: SHADOW,
								whiteSpace: 'nowrap',
							}}
						>
							{t.label}
						</div>
					</div>
				);
			})}
		</div>
	);
};

/** The five gadgets as an inventory card. */
const GadgetStrip: React.FC<{u: number}> = ({u}) => (
	<div
		style={{
			display: 'flex',
			gap: 18 * u,
			padding: `${16 * u}px ${26 * u}px ${14 * u}px`,
			background: 'rgba(5,9,15,0.72)',
			border: `${2 * u}px solid rgba(140,196,255,0.28)`,
			borderRadius: 14 * u,
			boxShadow: '0 20px 50px rgba(0,0,0,0.55)',
		}}
	>
		{GADGETS.map((g) => (
			<div key={g.img} style={{width: 132 * u, textAlign: 'center'}}>
				<div
					style={{
						width: 108 * u,
						height: 108 * u,
						margin: '0 auto',
						background: 'radial-gradient(circle, rgba(90,190,255,0.32), rgba(0,0,0,0) 66%)',
					}}
				>
					<Img src={staticFile(g.img)} style={{width: '100%', height: '100%'}} />
				</div>
				<div
					style={{
						fontFamily: TEXT,
						fontWeight: 600,
						fontSize: 17 * u,
						letterSpacing: '0.08em',
						color: 'rgba(232,238,245,0.92)',
						marginTop: 6 * u,
						lineHeight: 1.15,
					}}
				>
					{g.name}
				</div>
			</div>
		))}
	</div>
);

/** The MISSION FAILED newspaper lifted off the dimmed scene and laid on the right. */
const Newspaper: React.FC<{src: string; height: number; cx: number; cy: number}> = ({src, height, cx, cy}) => {
	// The sheet and its three buttons occupy x 22.5%..77.5% of the 2304x1296 source.
	const x0 = 0.225;
	const x1 = 0.775;
	const scale = height / 1296;
	const w = (x1 - x0) * 2304 * scale;
	return (
		<div
			style={{
				position: 'absolute',
				left: cx - w / 2,
				top: cy - height / 2,
				width: w,
				height,
				overflow: 'hidden',
				transform: 'rotate(-2.2deg)',
				filter: 'drop-shadow(0 30px 60px rgba(0,0,0,0.75))',
				WebkitMaskImage: 'radial-gradient(ellipse 74% 86% at 50% 50%, #000 82%, transparent 100%)',
				maskImage: 'radial-gradient(ellipse 74% 86% at 50% 50%, #000 82%, transparent 100%)',
			}}
		>
			<Img src={staticFile(src)} style={{position: 'absolute', height, width: 2304 * scale, left: -x0 * 2304 * scale, top: 0}} />
		</div>
	);
};

export const Screenshot: React.FC<{shot: Shot; fmt: FormatId}> = ({shot, fmt}) => {
	const {W, H, u} = useFrame();
	const stack = fmt === 'ipad';
	const side = shot.side ?? 'left';
	const vpos = shot.vpos ?? 'top';
	const zoom = shot.zoom?.[fmt] ?? 1;
	const ph = Math.round((W * 9) / 16);

	if (shot.kind === 'news') {
		return (
			<AbsoluteFill style={{background: INK}}>
				{/* The failure card dims the level to about rgb(23,32,40); match it so the lifted sheet's edges vanish. */}
				<AbsoluteFill style={{background: 'radial-gradient(ellipse 60% 75% at 70% 46%, #17202a 0%, #10171e 55%, #070a0e 100%)'}} />
				<Newspaper src={shot.src} height={H * (stack ? 0.78 : 0.94)} cx={W * (stack ? 0.72 : 0.71)} cy={H * 0.5} />
				<div style={{position: 'absolute', left: 0.055 * W, top: '50%', transform: 'translateY(-50%)'}}>
					<Caption shot={shot} u={u} align="left" maxWidth={stack ? 640 : 720} />
				</div>
			</AbsoluteFill>
		);
	}

	if (shot.kind === 'collage') {
		const top = stack ? H - ph : 0;
		return (
			<AbsoluteFill style={{background: INK}}>
				{stack ? <Gameplay src={TILES[0].src} focus={[0.5, 0.2]} zoom={1.2} filter="blur(46px) brightness(0.38)" /> : null}
				<Collage top={top} height={stack ? ph : H} />
				<AbsoluteFill
					style={{background: `linear-gradient(180deg, rgba(3,6,11,${stack ? 0.0 : 0.82}) 0%, rgba(3,6,11,0) ${stack ? 0 : 46}%)`}}
				/>
				{stack ? (
					<div
						style={{
							position: 'absolute',
							left: 0,
							top: top - 2,
							width: W,
							height: 0.12 * H,
							background: `linear-gradient(180deg, rgba(3,6,11,0.9), rgba(3,6,11,0))`,
						}}
					/>
				) : null}
				<div style={{position: 'absolute', left: 0, right: 0, top: (stack ? 0.085 : 0.07) * H, display: 'flex', justifyContent: 'center'}}>
					<Caption shot={shot} u={u} align="center" titleSize={stack ? 150 : 132} maxWidth={1400} />
				</div>
			</AbsoluteFill>
		);
	}

	const extras =
		shot.kind === 'gadgets' ? (
			<div style={{position: 'absolute', left: 0, right: 0, bottom: (stack ? 0.035 : 0.045) * (stack ? ph : H), display: 'flex', justifyContent: 'center'}}>
				<GadgetStrip u={u * (stack ? 1.05 : 1)} />
			</div>
		) : null;

	if (stack) {
		return (
			<AbsoluteFill style={{background: INK}}>
				<StackStage src={shot.src} focus={shot.focus} zoom={zoom}>
					{extras}
				</StackStage>
				<div style={{position: 'absolute', left: 0.06 * W, top: 0.07 * H}}>
					{shot.kind === 'hero' ? (
						<div style={{marginBottom: 18 * u, marginLeft: -0.012 * W}}>
							<Logo width={0.3 * W} />
						</div>
					) : null}
					<Caption shot={shot} u={u} align="left" titleSize={shot.kind === 'hero' ? 118 : 128} maxWidth={1500} />
				</div>
			</AbsoluteFill>
		);
	}

	const box: React.CSSProperties = {position: 'absolute', [side]: 0.055 * W, [vpos]: (vpos === 'top' ? 0.09 : 0.08) * H};
	return (
		<AbsoluteFill style={{background: INK}}>
			<Gameplay src={shot.src} focus={shot.focus} zoom={zoom} />
			<Scrim side={side} vpos={vpos} strength={shot.kind === 'hero' ? 0.9 : 1} />
			{extras}
			<div style={box}>
				{shot.kind === 'hero' ? (
					<div style={{marginBottom: 14 * u, marginLeft: -0.01 * W}}>
						<Logo width={(fmt === 'iphone' ? 0.3 : 0.38) * W} />
					</div>
				) : null}
				<Caption shot={shot} u={u} align={side} titleSize={shot.kind === 'hero' ? 104 : 128} maxWidth={shot.kind === 'hero' ? 900 : 760} />
			</div>
		</AbsoluteFill>
	);
};
