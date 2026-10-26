# Vibe TV

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

This version is a personal launcher for the owner of a TCL Smart TV Pro running Android 12, used from the sofa with the TV remote.

## Product Purpose

Provide a calm home screen for launching installed apps, viewing weather, browsing recommendations, and resuming supported media. Settings should be easy to scan and navigate without a pointer.

## Operating Context

Native Java/Canvas Android TV app, built with Java 17 and build.sh. The current hardware reference is 1920 × 1080. Wi-Fi ADB and fresh hardware screenshots are used for verification. Other platform implementations exist but this work targets the owner’s TCL only.

## Capabilities and Constraints

Preserve an easy recovery path to the original launcher and the watchdog. Keep the existing launcher selection and TCL boot permission. Preserve Continue Watching integration. Never read, print, or commit config.properties or signing material. Do not alter system picture, sound, network, or account preferences.

Home Screen offers four choices in this order: **Vibe TV 1**, the primary theme; **Simba (Free-My-TV)**, the original upstream home from commit `90908dc5bce178feb52e9868947cc039c64730fc`; **Lamplight**, an independent additional design; and **Google TV**, the factory-launcher recovery choice. Simba must preserve the original upstream composition and behavior rather than approximate it with a new design. Lamplight is implemented by its own renderer and Android adapter.

Only Vibe TV 1 exposes **Home sections** for reordering or hiding its sections. Theme choice is independent from the light/dark appearance preference. Shared settings and playback data must survive theme changes.

Continue Watching support depends on each provider's media-session data. YouTube and Stremio title/artwork/progress have been verified on the TCL. Netflix and Prime Video use explicit app-level fallback cards when useful metadata is absent; exact titles, thumbnails and reliable automatic resume are not established for those providers. JioHotstar supplies episode title/progress but no verified thumbnail. [PLAYBACK_STATUS.md](PLAYBACK_STATUS.md) is the provider-status reference; do not upgrade its claims merely because a theme can launch an app.

## Brand Commitments

The public project name is **Vibe TV**. **Free My TV** remains the upstream attribution and current runtime label, package name, and APK name. Keep the existing fonts and icon family and the primary home screen’s calm dark visual language. Settings should feel part of the same product while showing the available themes accurately.

## Evidence on Hand

The home design reference is 64-returned-home.png supplied by the user. Current hardware captures are stored locally under /tmp/free-my-tv-tv-check/. DESIGN.md records the approved home behavior.

## Product Principles

- Remote navigation must remain predictable across sections and controls.
- Show current values and contextual choices without redundant settings.
- Use screen space for useful content, with natural continuous scrolling.
- Keep recovery to the original home screen available.

## Accessibility & Inclusion

Maintain visible focus at sofa distance and the existing TalkBack virtual nodes.
