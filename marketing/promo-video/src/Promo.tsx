import React from 'react';
import {AbsoluteFill, Audio, interpolate, Sequence, staticFile, useCurrentFrame, Easing} from 'remotion';
import {Footage, FootageProps} from './components/Footage';
import {Flash, Glitch, Grain, Letterbox, Vignette} from './components/Overlays';
import {Kicker, KineticTitle, LevelTag} from './components/Type';
import {CountUp, GadgetRow, LogoSting} from './components/Brand';
import {TorchReveal} from './components/Reveal';
import {COLORS} from './theme';

/**
 * The whole cut, in frames at 60 fps. Every beat is one entry, so re-timing to the music the owner
 * supplies later means editing these numbers only.
 */
export const T = {
	hook: {from: 0, dur: 160},
	caught: {from: 160, dur: 80},
	logo: {from: 240, dur: 150},
	sneak: {from: 390, dur: 270},
	move: {from: 660, dur: 330},
	security: {from: 990, dur: 300},
	montage: {from: 1290, dur: 420},
	gadgets: {from: 1710, dur: 190},
	end: {from: 1900, dur: 320},
};
export const TOTAL_FRAMES = T.end.from + T.end.dur;

/** Set to the track's file under public/ (e.g. 'music.mp3') once the owner supplies it. */
const MUSIC: string | null = null;

type Shot = FootageProps & {dur: number};

/** Plays shots back to back with a short glitch on every cut. */
const Shots: React.FC<{shots: Shot[]; glitch?: boolean}> = ({shots, glitch = true}) => {
	let at = 0;
	return (
		<>
			{shots.map((sh, i) => {
				const from = at;
				at += sh.dur;
				const {dur, ...props} = sh;
				return (
					<Sequence key={i} from={from} durationInFrames={dur}>
						<Footage {...props} />
						{glitch && i > 0 ? <Glitch frames={6} seedKey={`c${i}${props.clip}`} /> : null}
					</Sequence>
				);
			})}
		</>
	);
};

const Hook: React.FC = () => (
	<>
		<TorchReveal
			path={[
				[0, 1500, 560],
				[70, 1080, 540],
				[120, 820, 520],
			]}
			radius={300}
			openAt={120}
			openFrames={26}
		>
			<Footage clip="l3_caught640.mp4" from={12.4} focusX={42} zoom={[1.25, 1.12]} origin={[45, 45]} />
		</TorchReveal>
		<Sequence from={14} durationInFrames={112}>
			<Kicker text="ONE LIGHT." x={130} y={140} size={64} color={COLORS.white} />
		</Sequence>
		<Sequence from={74} durationInFrames={52}>
			<Kicker text="ONE MISTAKE." x={130} y={220} size={64} color={COLORS.alarm} />
		</Sequence>
	</>
);

const Caught: React.FC = () => {
	const frame = useCurrentFrame();
	const punch = interpolate(frame, [0, 10], [1.28, 1.18], {extrapolateRight: 'clamp', easing: Easing.out(Easing.cubic)});
	return (
		<>
			<Sequence durationInFrames={34}>
				<Footage clip="l3_caught640.mp4" from={14.25} focusX={42} zoom={[punch, punch + 0.03]} origin={[62, 40]} />
				<AbsoluteFill style={{background: 'rgba(255,40,40,0.18)', mixBlendMode: 'multiply'}} />
				<Flash frames={10} peak={0.55} color={COLORS.alarm} />
			</Sequence>
			<Sequence from={34}>
				<Footage clip="l3_caught640.mp4" from={16.0} zoom={[1.08, 1.0]} origin={[50, 45]} />
				<Glitch frames={8} seedKey="failed" />
				<Flash frames={6} peak={0.4} />
			</Sequence>
		</>
	);
};

const Logo: React.FC = () => (
	<>
		<Footage clip="l2.mp4" from={9} zoom={[1.1, 1.16]} dim={0.62} blur={6} />
		<Flash frames={8} peak={0.7} />
		<LogoSting y={470} width={1180} tagline="STAY IN THE SHADOWS" taglineAt={34} />
	</>
);

const Sneak: React.FC = () => (
	<>
		<Shots
			shots={[
				{clip: 'l3.mp4', from: 20.2, dur: 140, focusX: 55, zoom: [1.05, 1.12], origin: [55, 55]},
				{clip: 'l6.mp4', from: 19.4, dur: 130, focusX: 50, zoom: [1.12, 1.04], origin: [50, 60]},
			]}
		/>
		<Sequence from={8}>
			<KineticTitle text="SNEAK" size={250} x={120} y={330} accent={COLORS.amber} exitAt={250} />
		</Sequence>
		<Sequence from={36}>
			<Kicker text="PAST GUARDS WHO NEVER BLINK" x={126} y={384} size={40} color={COLORS.white} />
		</Sequence>
	</>
);

const WORDS_MOVE = [
	{text: 'SWING', at: 0},
	{text: 'LEAP', at: 110},
	{text: 'CLIMB', at: 220},
];

const Move: React.FC = () => (
	<>
		<Shots
			shots={[
				{clip: 'l5.mp4', from: 12.2, dur: 110, focusX: 60, zoom: [1.1, 1.18], origin: [60, 35]},
				{clip: 'l2.mp4', from: 19.0, dur: 110, focusX: 50, zoom: [1.08, 1.14], origin: [50, 45]},
				{clip: 'l6.mp4', from: 34.6, dur: 110, focusX: 45, zoom: [1.06, 1.12], origin: [40, 50]},
			]}
		/>
		{WORDS_MOVE.map((w) => (
			<Sequence key={w.text} from={w.at + 4} durationInFrames={106}>
				<KineticTitle text={w.text} size={260} align="right" x={120} y={330} accent={COLORS.amber} exitAt={96} />
			</Sequence>
		))}
	</>
);

const Security: React.FC = () => (
	<>
		<Shots
			shots={[
				{clip: 'l4.mp4', from: 28.0, dur: 100, focusX: 30, zoom: [1.55, 1.65], origin: [28, 45]},
				{clip: 'l7.mp4', from: 24.0, dur: 100, focusX: 60, zoom: [1.45, 1.52], origin: [70, 48]},
				{clip: 'l4.mp4', from: 108.6, dur: 100, focusX: 55, zoom: [1.55, 1.62], origin: [62, 52]},
			]}
		/>
		<Sequence from={6}>
			<KineticTitle text="OUTSMART" size={200} x={120} y={260} accent={COLORS.alarm} exitAt={280} underline={false} />
		</Sequence>
		<Sequence from={18}>
			<KineticTitle text="SECURITY" size={200} x={120} y={450} color={COLORS.alarm} accent={COLORS.alarm} exitAt={268} />
		</Sequence>
		<Sequence from={60}>
			<Kicker text="LASERS  ·  CAMERAS  ·  DRONES" x={126} y={500} size={40} color={COLORS.white} />
		</Sequence>
	</>
);

const MONTAGE: (Shot & {num: string; name: string})[] = [
	{num: '02', name: 'Cargo Yard', clip: 'l2.mp4', from: 36.2, dur: 70, zoom: [1.1, 1.16], origin: [50, 40]},
	{num: '05', name: 'The Crane Yard', clip: 'l5.mp4', from: 7.4, dur: 70, zoom: [1.06, 1.12]},
	{num: '07', name: 'Service Tunnel', clip: 'l7.mp4', from: 87.6, dur: 70, focusX: 70, zoom: [1.5, 1.56], origin: [75, 48]},
	{num: '08', name: 'Relocation', clip: 'l8_generic.mp4', from: 6.6, dur: 70, focusX: 40, zoom: [1.2, 1.26], origin: [35, 60]},
	{num: '10', name: 'Below the Yard', clip: 'l10.mp4', from: 34.4, dur: 70, focusX: 30, zoom: [1.35, 1.42], origin: [25, 55]},
	{num: '11', name: 'The Prisoner', clip: 'l11.mp4', from: 144.4, dur: 70, focusX: 60, zoom: [1.35, 1.42], origin: [60, 85]},
];

const Montage: React.FC = () => {
	let at = 0;
	return (
		<>
			<Shots shots={MONTAGE} />
			{MONTAGE.map((m, i) => {
				const from = at;
				at += m.dur;
				return (
					<Sequence key={m.num} from={from + 2} durationInFrames={m.dur - 2}>
						<LevelTag num={m.num} name={m.name} x={120} y={940} />
					</Sequence>
				);
			})}
			<Sequence from={4} durationInFrames={400}>
				<CountUp to={12} label="MISSIONS" x={110} y={90} frames={40} />
			</Sequence>
		</>
	);
};

const Gadgets: React.FC = () => (
	<>
		<Footage clip="l6.mp4" from={40.5} zoom={[1.12, 1.2]} dim={0.55} blur={10} />
		<Flash frames={6} peak={0.35} />
		<KineticTitle text="GEAR UP" size={200} align="center" y={330} accent={COLORS.amber} underline />
		<Sequence from={20}>
			<GadgetRow y={630} size={210} gap={90} />
		</Sequence>
	</>
);

const End: React.FC = () => {
	const frame = useCurrentFrame();
	const dimIn = interpolate(frame, [70, 96], [0, 0.72], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const blur = interpolate(frame, [70, 96], [0, 12], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<>
			<Footage clip="l1.mp4" from={38.2} zoom={[1.16, 1.3]} origin={[50, 42]} blur={blur} />
			<AbsoluteFill style={{background: `rgba(0,0,0,${dimIn})`}} />
			<Sequence from={86}>
				<LogoSting y={420} width={1060} tagline="PLAY FREE NOW" taglineAt={30} />
			</Sequence>
			<Sequence from={150}>
				<Kicker text="ANDROID  ·  iOS" x={0} y={800} align="center" size={34} color={COLORS.steel} />
			</Sequence>
		</>
	);
};

export const Promo: React.FC = () => {
	const frame = useCurrentFrame();
	// Cinematic bars ease in for the story beats and step aside for the logo and end card.
	const bars = interpolate(frame, [0, 20, T.logo.from - 6, T.logo.from, T.sneak.from, T.sneak.from + 12, T.end.from, T.end.from + 20], [0, 1, 1, 0, 0, 1, 1, 0], {
		extrapolateRight: 'clamp',
	});
	return (
		<AbsoluteFill style={{backgroundColor: COLORS.ink}}>
			<Sequence from={T.hook.from} durationInFrames={T.hook.dur}><Hook /></Sequence>
			<Sequence from={T.caught.from} durationInFrames={T.caught.dur}><Caught /></Sequence>
			<Sequence from={T.logo.from} durationInFrames={T.logo.dur}><Logo /></Sequence>
			<Sequence from={T.sneak.from} durationInFrames={T.sneak.dur}><Sneak /></Sequence>
			<Sequence from={T.move.from} durationInFrames={T.move.dur}><Move /><Glitch frames={6} seedKey="mv" /></Sequence>
			<Sequence from={T.security.from} durationInFrames={T.security.dur}><Security /><Flash frames={8} peak={0.5} color={COLORS.alarm} /></Sequence>
			<Sequence from={T.montage.from} durationInFrames={T.montage.dur}><Montage /><Flash frames={6} peak={0.5} /></Sequence>
			<Sequence from={T.gadgets.from} durationInFrames={T.gadgets.dur}><Gadgets /></Sequence>
			<Sequence from={T.end.from} durationInFrames={T.end.dur}><End /></Sequence>
			<Vignette />
			<Letterbox amount={bars} size={70} />
			<Grain />
			{MUSIC ? <Audio src={staticFile(MUSIC)} /> : null}
		</AbsoluteFill>
	);
};
