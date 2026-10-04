import React from 'react';
import {AbsoluteFill, Sequence, useVideoConfig} from 'remotion';
import {AlertPulse, BeamReveal, DoorWipe, Dossier, Flash, GlitchText, Grain, LaserReveal, Label, Letterbox} from './components/Fx';
import {Kinetic} from './components/Kinetic';
import {EndCard, GadgetBurst, MissionGrid, TILE_ZOOM, Tile} from './components/Scenes';
import {Shot} from './components/Shot';
import {ShotId} from './shots';
import {C} from './theme';

// ---- Timeline (frames at 60 fps; 1800 = 30 s). Shared by the 16:9 and 9:16 cuts. -------------

// Hook
const A1 = 0;
const A2 = 93;
// Every light is a trap: beam swings in over A2's tail
const B = 168;
const BEAM = 28;
// Montage: five verbs, 84 frames each
const M = 360;
const MLEN = 84;
const MONTAGE: {id: ShotId; word: string}[] = [
	{id: 'S05', word: 'SNEAK'},
	{id: 'S06', word: 'CLIMB'},
	{id: 'S07', word: 'LEAP'},
	{id: 'S08', word: 'PUSH'},
	{id: 'S11', word: 'RUN'},
];
// Mission grid, pushing into the tile the next scene continues
const G = M + MONTAGE.length * MLEN; // 780
const GDUR = 230;
const GZOOM_AT = 172;
const GZOOM = 58;
const MISSIONS = '12 MISSIONS';
const TILES: Tile[] = [
	{id: 'G01', caption: '01  NIGHT ARRIVAL'},
	{id: 'G02', caption: '02  CARGO YARD'},
	{id: 'S16', caption: '04  MOVING TARGET'},
	{id: 'G05', caption: '05  THE CRANE YARD'},
	{id: 'G06', caption: '06  STOLEN MANIFEST'},
	{id: 'G11', caption: '11  THE PRISONER'},
];
const TARGET = 2;
const DOSSIER = {mission: 'Mission 04', name: 'MOVING TARGET'};
// Don't get seen
const E1 = G + GDUR; // 1010: S16 continues full screen
const E2 = 1190; // caught
const LASER = 22;
// Escort, behind the roll-up door
const F = 1320;
// Gadgets
const GG = 1500;
// End card
const END = 1632;
export const PROMO_FRAMES = 1800;

/**
 * Where text sits in each format. The 9:16 cut keeps copy inside the band vertical feeds leave
 * clear (below ~250 px from the top, above ~450 px from the bottom, off the right-hand buttons).
 */
const LAYOUT = {
	landscape: {
		head: 150,
		headL: 120,
		oneTop: 330,
		zeroTop: 482,
		lightAlign: 'right' as const,
		lightPos: {right: 120, top: 300},
		label: {left: 120, bottom: 118, size: 190},
		dossier: {left: 110, top: 150},
		seen: {text: "DON'T GET SEEN.", size: 190, pos: {left: 120, top: 150}},
		breakTop: 150,
	},
	portrait: {
		head: 124,
		headL: 80,
		oneTop: 300,
		zeroTop: 434,
		lightAlign: 'left' as const,
		lightPos: {left: 80, top: 300},
		// Top of the frame: the player runs along the ground in the lower half of a full-height crop.
		label: {left: 80, top: 300, size: 170},
		dossier: {left: 80, top: 300},
		seen: {text: "DON'T GET\nSEEN.", size: 180, pos: {left: 80, top: 290}},
		breakTop: 300,
	},
};

export const Promo: React.FC = () => {
	const {width, height} = useVideoConfig();
	const portrait = height > width;
	const L = portrait ? LAYOUT.portrait : LAYOUT.landscape;
	return (
		<AbsoluteFill style={{background: C.ink}}>
			{/* Hook */}
			<Sequence from={A1} durationInFrames={A2 - A1}>
				<Shot id="S01" dur={A2 - A1} />
			</Sequence>
			<Sequence from={A2} durationInFrames={B + BEAM - A2}>
				<Shot id="S02" dur={B + BEAM - A2} />
			</Sequence>
			{portrait ? null : <Letterbox outAt={B - 2} />}
			<Sequence from={0} durationInFrames={B + 10}>
				<Kinetic lines={[{text: 'ONE THIEF.', size: L.head}]} at={14} out={B - 12} style={{left: L.headL, top: L.oneTop}} />
				<Kinetic lines={[{text: 'ZERO WITNESSES.', size: L.head}]} at={A2 + 4} out={B - 10} style={{left: L.headL, top: L.zeroTop}} />
			</Sequence>

			{/* Every light is a trap */}
			<Sequence from={B} durationInFrames={M - B}>
				<BeamReveal dur={BEAM}>
					<Shot id="S03" dur={M - B} />
				</BeamReveal>
				<Kinetic
					lines={[
						{text: 'EVERY LIGHT', size: L.head, accent: {word: 'LIGHT', color: C.torch}},
						{text: 'IS A TRAP.', size: L.head},
					]}
					at={BEAM + 6}
					out={M - B - 18}
					align={L.lightAlign}
					style={L.lightPos}
				/>
			</Sequence>

			{/* Montage */}
			{MONTAGE.map((m, i) => (
				<Sequence key={m.id} from={M + i * MLEN} durationInFrames={MLEN}>
					<Shot id={m.id} dur={MLEN} />
					<Label word={m.word} index={i + 1} total={MONTAGE.length} {...L.label} />
					<Flash at={0} strength={m.id === 'S07' ? 1 : 0.28} />
				</Sequence>
			))}

			{/* Mission grid */}
			<Sequence from={G} durationInFrames={GDUR}>
				<MissionGrid tiles={TILES} target={TARGET} zoomAt={GZOOM_AT} zoomDur={GZOOM} dur={GDUR} title={MISSIONS} />
			</Sequence>

			{/* Don't get seen: the grid tile carries on full screen, then the laser cuts to a catch */}
			<Sequence from={E1} durationInFrames={E2 + LASER - E1}>
				<Shot id="S16" dur={E2 + LASER - E1} offset={GDUR} zoom={[TILE_ZOOM, portrait ? 1.1 : 1.14]} />
				<Dossier mission={DOSSIER.mission} name={DOSSIER.name} out={120} {...L.dossier} />
			</Sequence>
			<Sequence from={E2} durationInFrames={F - E2}>
				<LaserReveal dur={LASER}>
					<Shot id="S17" dur={F - E2} />
					<AlertPulse at={LASER - 4} />
				</LaserReveal>
				<GlitchText text={L.seen.text} at={LASER + 4} size={L.seen.size} style={L.seen.pos} />
			</Sequence>

			{/* Escort */}
			<Sequence from={F} durationInFrames={GG - F}>
				<Shot id="S19" dur={GG - F} />
				<Kinetic lines={[{text: 'BREAK HIM OUT.', size: L.head}]} at={30} out={GG - F - 16} style={{left: L.headL, top: L.breakTop}} />
			</Sequence>
			<DoorWipe closeAt={F} />

			{/* Gadgets */}
			<Sequence from={GG} durationInFrames={END - GG}>
				<GadgetBurst bg="S21" dur={END - GG} />
				<Flash at={0} color={C.cold} strength={0.5} />
			</Sequence>

			{/* End card */}
			<Sequence from={END} durationInFrames={PROMO_FRAMES - END}>
				<EndCard bg="END" dur={PROMO_FRAMES - END} />
			</Sequence>

			<Grain />
		</AbsoluteFill>
	);
};
