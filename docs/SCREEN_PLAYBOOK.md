# Screen Implementation Playbook (for all agents)

Every screen in this app is rebuilt from Figma (visual truth) with Swift/RN behavior.
Read this fully before writing any screen.

## Sources
- Figma file key: `N4k6jzpeLZgeRS5O1xfyIv`. Load tools via ToolSearch
  (`select:mcp__plugin_brand-voice_figma__get_design_context,mcp__plugin_brand-voice_figma__get_metadata,mcp__plugin_brand-voice_figma__download_assets`),
  then call get_design_context with your screen's nodeId (clientLanguages kotlin,
  clientFrameworks android,compose). Use the returned React/Tailwind + screenshot as spec.
- Swift behavior: /Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/Figma<Screen>ViewController.swift
- RN reference: /Users/codecityceo/Documents/GitHub/AD-REACT_NATIVE_APP-OLD/src/modules/*
- API contract: app/src/main/java/com/ga/airdrop/data/ (models, api service, repos)

## Conventions (established — follow, do not reinvent)
- Package: `com.ga.airdrop.feature.<group>` — one `<Name>Screen.kt` (+ `<Name>ViewModel.kt` when it loads data).
- MVVM: ViewModel with `MutableStateFlow<UiState>` + `val state: StateFlow<...>`; screens use
  `viewModel.state.collectAsState()`. Repos injected via default constructor params.
- Theme: `AirdropTheme.colors.*` (gray100–700, textDarkTitle, textDescription, iconShape, divider,
  peachLight, glassOverlay20/70), `BrandPalette.*`, `AlertPalette.*`, `TierPalette.*`.
  Typography: `AirdropType.h1–h6/title1–2/subtitle1–3/body1–3/button/underlineLink`.
  Spacing: `Spacing.xs(5)/sm(10)/sm1(15)/md(20)/lg(30)/xl(40)`. Radius: `Radius.xxs–xl` (cards = s(15)).
- Shared components (core/designsystem/components): AirdropHeader, AirdropBottomBar (never build your own),
  GradientButton, OutlineButton, TypeInputField, ThemeToggle.
- Icons: check `res/drawable/ic_*.xml` FIRST (90 converted from the real RN/Figma SVG set).
  Missing icon → download from your Figma design-context asset URLs (curl the URL), convert the SVG
  to a VectorDrawable verbatim (arc flags must be space-separated for Android). NEVER draw your own paths.
- Raster assets → `res/drawable-nodpi/img_*.png` (download from Figma asset URLs or copy from RN
  `src/assets/images`). Match the file's actual format to its extension.
- Figma dp values are used verbatim (375-wide frames). Use exact paddings/gaps/sizes/text styles from
  the design context — no approximation. Screens that sit under the header add `AirdropHeader` overlay
  (Box + verticalScroll content). Tab-root screens leave ~90dp bottom clearance for the glass bar.
- Navigation: `Routes.*` constants; screens receive `onNavigate: (String) -> Unit` or typed lambdas.
  Detail screens receive their argument via NavHost route args (see Routes.kt helpers).
- Reference files to copy patterns from: `feature/home/HomeScreen.kt`, `feature/contacts/ContactsScreen.kt`
  (solid-header tab + copy actions), `feature/auth/LoginScreen.kt` (forms).
- Tab headers: `AirdropHeader(style = AirdropHeaderStyle.Solid)` on non-Home tabs, data from
  `SessionStore.header.collectAsState()`. Home uses OverImage style.
- MULTI-AGENT RULES: do NOT edit files outside your feature group. Do NOT edit AppRoot.kt or
  Routes.kt — instead write `feature/<group>/<Group>Nav.kt` exposing
  `fun NavGraphBuilder.<group>Graph(navController: NavHostController)` with your composable()
  registrations (use route-string literals matching Routes.kt), plus a note of any NEW route
  constants needed; the orchestrator wires them. Check res/drawable before adding icons; name
  screen-specific assets `ic_<group>_*`/`img_<group>_*` to avoid collisions. Do NOT run gradle.

## Known Swift gaps — implement properly on Android
authorized-user edit (reuse Add form in edit mode), profile avatar upload/delete, live notifications
feed, payment invoice download action, typed drop-alert multipart, shop search 500ms debounce,
full logout hygiene (clear token + FCM + local stores).

## Definition of done for a screen
1. Matches the Figma design context (structure, spacing, type styles, colors both modes).
2. Behavior matches Swift VC (or RN when Swift is stubbed).
3. Wired to the real repo/API where the screen loads data (loading + error states).
4. Registered in core/navigation/AppRoot.kt mainGraph.
5. Compiles (do NOT run gradle if another build is running — coordinate).
