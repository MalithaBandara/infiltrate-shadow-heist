package com.infiltrate.ui

/**
 * Whether the menu screens inset themselves from the host's reported safe area.
 *
 * The insets themselves are measured once per platform and published to `game.model.DeviceScreen`
 * (see `ui/Responsive.kt`'s [safeAreaPadding]); this flag decides whether the Compose menus act on
 * them. The gameplay HUD is unaffected either way - it reads `DeviceScreen` directly and keeps its
 * own floors.
 *
 * - **iOS: true.** A landscape iPhone puts the Dynamic Island / notch on a *side* - 59pt wide, so
 *   anything pinned to that edge lands underneath it - and keeps a strip along the bottom for the
 *   home indicator. There is no way to draw usefully in either.
 * - **Android: false.** The set published there is `displayCutout() or mandatorySystemGestures()`,
 *   and on the owner's own phone (2026-09-25) that came back as a band down one long edge and
 *   another along the bottom, which showed up as the menus not using the whole screen: "in
 *   androids there should not be padding at all". These screens are drawn edge to edge under
 *   hidden system bars, and the panel is opaque flat colour behind the content, so a cutout over
 *   it costs nothing - the content that matters sits well inboard of both edges already.
 * - **Desktop JVM: false.** Nothing publishes insets there, so this only makes the intent
 *   explicit rather than changing any behaviour.
 */
expect val menuAppliesSafeAreaInsets: Boolean
