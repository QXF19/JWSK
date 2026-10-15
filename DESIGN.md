---
name: JWSK
description: Native Android root management with the supplied furry identity and Miuix glass.
colors:
  artwork-night: "#04172D"
  status-backing-light: "rgba(255, 255, 255, 0.94)"
typography:
  status-headline:
    fontFamily: "sans-serif"
    fontSize: "28sp"
    fontWeight: 700
  brand-title:
    fontFamily: "sans-serif"
    fontSize: "24sp"
    fontWeight: 700
  tools-title:
    fontFamily: "sans-serif"
    fontSize: "22sp"
  search:
    fontFamily: "sans-serif"
    fontSize: "17sp"
  status-reason:
    fontFamily: "sans-serif"
    fontSize: "15sp"
  summary:
    fontFamily: "sans-serif"
    fontSize: "14sp"
  floating-nav-label:
    fontFamily: "sans-serif"
    fontSize: "11sp"
    lineHeight: "14sp"
rounded:
  glass-panel: "22dp"
  icon-disc: "24dp"
  action-search: "28dp"
  badge: "12dp"
spacing:
  tight: "4dp"
  text-gap: "6dp"
  small: "8dp"
  list-gap: "12dp"
  artwork-gutter: "14dp"
  content: "16dp"
  detail: "18dp"
  bottom-clearance: "24dp"
components:
  glass-panel:
    rounded: "{rounded.glass-panel}"
  install-action:
    rounded: "{rounded.action-search}"
    padding: "14dp"
  settings-target:
    size: "48dp"
  tool-row:
    padding: "{spacing.content}"
  search-field:
    typography: "{typography.search}"
    rounded: "{rounded.action-search}"
    padding: "14dp"
  configuration-badge:
    rounded: "{rounded.badge}"
    padding: "{spacing.small}"
  floating-navigation:
    height: "64dp"
    padding: "4dp"
---

# Design System: JWSK

## Overview

**Creative North Star: "Native controls over the supplied furry world"**

This names the already approved combination: Google-style native Android information structure and system typography, the user's original furry character, and actual compose-miuix-ui controls and glass. The character provides identity; real backend state provides the headline and next action. This is a scan of the current Android implementation, not a proposed visual identity.

Home and Tools expose luminous cyan/blue facets with lavender reflections beneath restrained translucent containers. Authorization and policy surfaces use quieter native surfaces for application identity and controls. Their flat treatment is part of this implementation, not a missing illustration. The character header, generated lower field and native text are separate layers.

**Key Characteristics:**

- Supplied character identity, with broad blue artwork visible around native content.
- Theme-derived colors, system fonts and density-independent geometry.
- Glass where the backdrop exists; readable solid surfaces when blur is disabled.
- Distinct destinations and real state, without repeated shortcut cards.

Scope and evidence: current source lives under `kernelsu/manager/app/src/main/java/me/weishu/kernelsu/`, especially `jwsk/ui/JwskHomeScreen.kt`, `ToolsScreens.kt`, `JwskGlassLayout.kt`, `AlphaSuperUserScreen.kt`, `ui/theme/`, and `ui/component/bottombar/`. The actual direction contract is `.impeccable/direction-preview.md`; no `.impeccable/surface-briefs/` directory exists. `PRODUCT.md` records the confirmed native typography, character identity and unique-entry constraints. `.impeccable/mocks/home-unique.png` and `tools-unique.png` are approved concepts, not runtime captures.

The operative evidence is `.impeccable/native-evidence.json`, read with `.impeccable/review/finish-verdict.md`: all three final findings are resolved within that verdict's limited scope. Historical `.impeccable/build/state.json` web traversal, hero metric and DOM responsive gates are unsupported/skipped for Android, not passed. The ten native capture paths listed in the operative record cover phone, dark/1.3 font, expanded configurations and policy; they do not certify physical-device performance, animation smoothness, all backend operations or release readiness. `releaseApproved` remains false. This documentation makes no new release claim.

## Colors

The artwork supplies an icy cyan/blue atmosphere; controls take their foreground and surface colors from the active native theme.

### Primary

- **Native Theme Accent:** `MiuixTheme.colorScheme.primary` colors the install action, status symbol, tool icons and selected navigation content. Pair filled actions with `onPrimary`; never freeze the captured emulator blue as the universal brand token.

### Neutral

- **Theme Surface / Foreground:** `surface`, `background`, `onSurface`, `onBackground`, `onSurfaceVariantSummary` and `surfaceContainerHigh` remain native role lookups. The latter supports the authorization search field. Runtime key color, light/dark mode, Monet, palette style and color specification resolve them in `ui/theme/MiuixTheme.kt`.
- **Artwork Night:** the frontmatter's `artwork-night` darkens only the lower artwork field at 70% opacity; the original character layer remains separate.
- **Light Status Backing:** the frontmatter's `status-backing-light` stabilizes system glyph contrast only across `WindowInsets.statusBars`. Dark mode uses the theme surface and light system glyphs.
- **Native Permission Tag:** Alpha's granted ROOT tag uses `tertiaryContainer` at 60% opacity with `onTertiaryContainer`. This is a native state role, not a new fixed tertiary brand palette.

**The Native Role Rule.** Resolve interactive and text colors from the active Miuix/Material theme; artwork colors do not replace contrast-bearing native roles.

Only the two directly authored fixed colors are frontmatter primitives. Dynamic roles cannot be truthfully reduced to one CSS hex. The sidecar records their native bindings; any browser swatches/snippets are labeled approximations and are not replacement tokens.

## Typography

**Display Font:** Android system default sans-serif, with system Chinese fallback.
**Body Font:** the same system family through native Miuix Text.
**Label/Mono Font:** system sans for controls; `FontFamily.Monospace` only for Comput output.

**Character:** familiar Android hierarchy, with native Chinese glyphs and scalable text rather than lettering embedded in artwork. The user explicitly selected system typography; do not treat a display-font preference from a web checklist as authority to substitute it.

### Hierarchy

- **Status headline:** frontmatter `status-headline`, bold, used for actual loading/working/not-working state.
- **Brand / Tools title:** `brand-title` and `tools-title`; Tools does not explicitly force a weight.
- **Search text:** `search`, a single-line native field with a theme-colored cursor.
- **Status reason / summary:** `status-reason` and `summary`, wrapping factual content beneath the heading or device row.
- **Floating navigation label:** `floating-nav-label`, one line with visible overflow as implemented.
- **Inherited roles:** tool descriptions and application package names use `MiuixTheme.textStyles.body2`; configuration badges use `footnote1`; Alpha headings use `title1`/`title2`. Their library metrics remain inherited from Miuix 0.9.4 rather than invented here. Row titles and the install action use SemiBold (600).

The frontmatter uses native `sp`, not CSS pixels. Unspecified line heights, weights and tracking remain native defaults. `ui/theme/Type.kt` defines a Material bodyLarge override, but it is not evidence that the Miuix body2 role has those metrics.

**The Native Text Rule.** Keep titles, descriptions, status and controls as UTF-8 resources/native text and honor system font scaling; never bake interface text into a raster plate.

## Layout

Home and Tools are vertically scrolling native lists, centered with a maximum width of 720dp. Their artwork continues across the available window. Home uses the frontmatter list gap; Tools uses the content step. Artwork-page gutters use the artwork-gutter step; authorization list gutters are 12dp. Native system-bar and navigation insets are added to content padding, including a 24dp bottom clearance above navigation.

The character header height derives from window width: Home uses 554/851 capped at 300dp; compact Tools uses 295/851 capped at 180dp. These are the current crop rules, not fixed phone heights. The last 18% of the header fades into the independent lower field. Do not stretch screenshots to implement this relationship.

`ui/util/WindowSize.kt` selects split-pane eligibility at width >=840dp, or width >=600dp with height/width <1.2. `useNavigationRail` additionally suppresses rail mode when Miuix floating navigation is enabled. Thus expanded native captures can retain a floating bottom bar. Do not invent CSS media queries or infer a rail from device width alone. Alpha currently fills the available width; do not generalize Home's 720dp cap to it.

Tap targets include 48dp toolbar icons and a minimum 48dp install action/search field. Tool rows combine 16dp padding, a minimum 64dp inner row and 48dp icon discs; their descriptions wrap rather than shrinking to fit a fixed-height mock. These are native dp measurements, not browser gate scores.

## Elevation & Depth

`JwskGlassPanel` samples its actual native layer backdrop with vibrancy, 12dp horizontal/vertical blur, and an 8dp refraction height/amount lens. It then applies the theme surface at 62% in light mode or 42% in dark mode. With no backdrop it draws a solid theme surface. Text and controls stay native above that treatment.

### Shadow Vocabulary

- **Floating bar:** `FloatingBottomBar.kt` uses a native circular pill drop shadow with 10dp radius, black at 10% light / 20% dark. Its backdrop has 4dp blur, 24dp lens height/amount and 40% surfaceContainer. Specular highlights and a press-dependent inner shadow belong to that bar, not every container.
- **Content panels:** separation comes from translucent tonal material and artwork, not a repeated offset shadow. Alpha cards/search use native surface differentiation.

**The Backdrop Rule.** Keep the lower artwork visible through and around glass; when blur is off, use the implemented solid-surface fallback with readable text.

Floating navigation has actual native press/drag, highlight and spring behavior; `MainPagerState` uses the library page spring, or immediate scrolling in rail mode. These are implementation observations, not a measured smoothness claim. Motion constants and preview limitations are in the sidecar.

## Shapes

Glass containers use the frontmatter panel corner; primary action/search use the action-search corner. Tool icon discs are 48dp circles with the icon-disc corner, and status/configuration badges use the badge corner. The floating bar and selection pill use `CircleShape`, not a guessed corner radius. Native Card, preferences, menus and switch geometry remain Miuix defaults unless a call site explicitly overrides them.

Material/Miuix vector icons supply interface glyphs; the status symbol is a 64dp outlined native vector. Icons are not emoji or lettering. The install action remains a full-width pill; authorization remains a list of application rows, not a dashboard of duplicated feature cards.

## Components

### Buttons

The Home install action is a confident filled native pill: `primary`/`onPrimary`, frontmatter padding/corner and minimum 48dp height; it disables interaction while loading. Settings uses a 48dp target and a 24dp gear; Home gives that target a surface disc at 82% opacity. Other screens use native TextButton and IconButton with their library feedback. Preserve Compose click semantics and native indication; do not retrofit web hover behavior into Android.

### Chips

The Tools configuration badge uses native footnote1, 10% primary fill, onSurface text and the frontmatter badge padding/corner. Its state reflects completeness of required provider configuration. A transient connection failure does not create a second banner. Alpha's ROOT status tag is a separate permission state, not a filter or new route.

### Cards / Containers

Artwork-page containers use `JwskGlassPanel`; internal padding belongs to their content (Home status: 16dp; device rows: 18dp; tool rows: 16dp). Native Alpha Card uses 14dp inside margin for application rows, showIndication, a 48dp app icon, weighted label/package column, optional ROOT tag and trailing chevron. Do not assign an unverified custom corner to the upstream library Card.

### Inputs / Fields

Alpha search is a real BasicTextField on surfaceContainerHigh, primary cursor, frontmatter search padding/corner, 24dp search vector and 10dp icon/text gap. It has a 48dp minimum height, search IME and a 256-character input cap. Comput uses native Miuix TextField, a minimum of three lines, IME clearance and disabled-while-running behavior; output remains selectable monospace text. Error, popup and switch states remain native controls, not raster substitutions.

### Navigation

Four destinations are Home, Superuser, Modules and Tools. Material and Miuix navigation implementations remain selected by UI mode. The Miuix floating variant is 64dp high with 4dp internal padding, 56dp selection content, minimum 76dp tab width and 28dp outer horizontal margins. Bottom clearance is actual navigation-bar inset +8dp when present, otherwise 28dp. Selection, badge counts and keyboard actions come from the native implementation. Settings belongs to the top-right gear; Home's install action and kernel-version row are the unique installation and device-detail entry points for these surfaces. Tools has exactly AI Assistant, manual Comput and Logs. Do not add duplicate shortcut cards.

### Artwork Layers and Provenance

The supplied original is preserved at `.impeccable/mocks/furry-original.jpg`; its JSON sidecar identifies the user's network-source illustration. `assets/plates/furry-art.png` is the character header plate, copied byte-identically to `kernelsu/manager/app/src/main/res/drawable-nodpi/jwsk_furry_header.png`. The lower field at `assets/plates/furry-field.png` was generated from the approved Home reference solely as a text-free background, copied byte-identically to `drawable-nodpi/jwsk_furry_field.png`. Its full prompt is preserved at `.impeccable/build/background-field-prompt.txt` and embedded in the PNG according to the operative evidence. Generation is not claimed as original user artwork. Keep both assets and their distinct provenance.

Verified SHA-256 pairs: header `B1C1443CAE83E7B26D3B8B894C61580660F66BD41FEFA8533DA5C682863840C3`; field `E2E75186625D04D61FF9C518D245AE840ED247B99935F1646E91CDFC59CB626D`. The launcher identity must continue to derive from the user's original character, with face/ears/horns recognizable in the adaptive safe area; do not derive it by cropping a mockup.

## Do's and Don'ts

### Do:

- Do resolve colors from the active native theme and preserve system font scaling.
- Do keep the supplied character and generated lower field as separate, provenance-bearing artwork layers beneath native controls.
- Do preserve solid-surface blur fallback and stable system-glyph backing.
- Do preserve one entry per task and let real backend state determine the status.
- Do verify new surfaces in native light, dark, large-font and expanded configurations.

### Don't:

- Don't replace system typography, native controls or vector icons with rasterized interface artwork.
- Don't turn illustrative preview colors, CSS pixels or browser hover styles into Android tokens.
- Don't add duplicate cards, API banners or new tool destinations to fill open artwork space.
- Don't treat concept images, web scanner phases or emulator stills as physical-device or release certification.

Not canonized or repaired: unmeasured physical-device performance/motion and untouched backend coverage remain limitations; inherited library metrics and dynamic theme colors remain unresolved to fixed numeric tokens by design. This pass changes documentation only and introduces no new visual repairs.
