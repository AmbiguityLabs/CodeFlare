# Smart Selection Engine & Universal Drag-and-Drop Text System

EdgeCode gains a dedicated pure-Kotlin **Smart Structural Text Selection Engine** paired with a **Universal Android Drag-and-Drop Text Coordinator** so you can tap-and-hold text in the code editor, CLI/terminal outputs, console logs, or input fields—automatically capturing the exact semantic unit you need—and fluidly drag it anywhere inside the app or to/from other Android apps. Every feature is preserved in full and executed across **five strictly gated phases**, where each phase must compile cleanly and pass all unit tests before moving to the next.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Revised Per Your Feedback:** Zero features have been removed. Execution is now locked to your exact **5-Phase Gated Order**, with a mandatory **Compile + Unit Test Gate** after every single phase before advancing to the next.

- **Confirmed — Strict 5-Phase Gated Execution Order:**
  1. **Phase 1:** Pure-Kotlin Smart Selection Engine + exhaustive JVM unit tests → *Compile & Test Gate*
  2. **Phase 2:** Code Editor Smart Hold + Same-File Move + Atomic Compound Undo/Redo + unit tests → *Compile & Test Gate*
  3. **Phase 3:** CLI / Terminal / Log / Console / Response read-only smart drag sources + in-app text input drop targets → *Compile & Test Gate*
  4. **Phase 4:** Cross-App Android Drag & Drop (`DRAG_FLAG_GLOBAL` + `requestDragAndDropPermissions` + HTML/plain-text payload handling) → *Compile & Test Gate*
  5. **Phase 5:** Settings → Editor **"Selection & Drag"** full customization + Progressive Hold-to-Expand + `1.5x` Drop Target Loupe + final Debug/Release/Test verification → *Final Release-Parity Gate*
- **Confirmed — Universal Scope (In-App + Cross-App):** Spans the custom-canvas Code Editor, CLI Output/Logs/Headers, IDE Console, Live Tail, Problems, API Tester & Worker Test panels, in-app text inputs, and external Android apps (Chrome, Notes, Termux, split-screen windows).
- **Confirmed — Smart Hold + Customizable Hold Overlay:** Default hold automatically captures the smartest semantic unit with floating **Expand / Shrink** controls, with complete Settings controls for hold behavior and every visible overlay element.
- **Engineering Decision — Pure-Kotlin Multi-Domain Structural Engine (Over Tree-sitter NDK):** Handles both formal code (JS/TS/JSON/HTML/CSS) *and* Terminal/CLI/Log semantic entities (URLs, `file:line:col` stack frames, inline JSON, HTTP headers, UUIDs, CLI flags/commands) in under 1 ms with zero native ABI bloat, Android 15/16 16 KB safety, and 100% JVM unit testability.
- **UX Default — Context-Aware Move vs. Copy:** Same-file drags inside the editable editor default to **Move** (atomic 1-step Undo); drags out of the editor to secondary panes/external apps or from read-only CLI/logs default to **Copy**—with a one-tap **Move ⇄ Copy** override chip on the floating toolbar and configurable default in Settings.

---

## 1. Overview & Core Concept

- **What It Does:** Replaces basic single-word long-press selection with an intelligent, domain-aware **Selection Ladder** plus Chrome-class **Drag-and-Drop Text Movement**:
  1. **Smart Automatic Capture:** Tap-and-hold detects what sits under your finger—whether a URL inside a CLI log, a quoted string or JSON key-value pair, a function call, a bracketed block, a stack trace file path, or a code statement—and snaps selection to the exact needed boundaries immediately.
  2. **Fluid One-Motion or Two-Motion Drag:** Either slide your finger right after the hold haptic fires to carry the grabbed text in one continuous gesture, or release first to fine-tune with Expand/Shrink buttons or teardrop handles and drag from the selection body or toolbar grip.
  3. **Precision Drop Everywhere:** Drop directly onto any character position in the custom Code Editor (with a live animated drop caret, `1.5x` drop target loupe, and smooth viewport edge auto-scroll), into CLI/API/Find text inputs, or into other Android apps in split-screen/freeform mode—and accept text dragged *from* external apps back into EdgeCode.
- **Target Audience / Persona:** Mobile Cloudflare Workers & web developers editing code, debugging CLI/Live Tail output, and transferring endpoints, tokens, JSON payloads, or refactoring code blocks on phones, foldables, and tablets without a hardware mouse.
- **Key Value:** Eliminates tedious multi-step `select → drag tiny handles → copy → scroll → tap → paste → scroll back → delete` cycles, turning text transfer across code, terminal outputs, and external apps into a single natural touch gesture.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **One-Motion Smart Grab & Move (Code Editor):**
   - Press and hold on a code token, string literal, call expression, or JSON property (`~320ms` default, configurable).
   - A crisp haptic pulse confirms capture; the smart semantic range highlights and a compact **Scope Pill** (e.g., `String · 28ch` or `Call`) fades in above the selection.
   - Slide your finger past touch slop while still holding → the selection lifts into a frosted **Floating Drag Pill** positioned *above* your fingertip (preventing thumb occlusion).
   - While moving inside the same file in **Move** mode, the original selection softens to a dashed ghost outline (`35%` opacity) while a bright glowing **Vertical Drop Caret** (plus optional `1.5x` glass loupe in Phase 5) snaps character-by-character under your aim point.
   - Hover near the top, bottom, left, or right edge (`48dp` hot zone) to smoothly auto-scroll through long files.
   - Release to drop → text moves atomically, newly moved text stays briefly highlighted for confirmation, and a single Undo tap or three-finger left swipe reverts the entire move cleanly.
2. **Release, Refine Scope & Drag (Precision Workflow):**
   - Press and hold, then lift without dragging → the selection remains active with teardrop handles and the upgraded **Floating Selection Toolbar**.
   - Tap **`Expand (+)`** to climb the structural ladder (`Word → Member Chain → String Content → Quoted String → Call/Bracket Expr → Property/Statement → Block → Line → Full Document`) or **`Shrink (−)`** to step back down; the live **Scope Badge** updates at every step.
   - Optionally tap the **`Move / Copy`** pill to override the transfer mode, then hold the selection body or grab the dedicated **Drag Grip** chip to carry the text anywhere.
3. **Progressive Hold Mode (Phase 5):**
   - When selected in Settings, keeping your finger stationary after the initial hold steps upward through the structural ladder every `~450ms` with gentle haptic ticks and live Scope Pill updates; sliding at any moment locks that level and launches drag-and-drop immediately.
4. **CLI / Terminal / Log to Editor & External Apps (Phases 3–4):**
   - Press and hold on any URL, stack trace (`src/worker.ts:42:13`), inline JSON object, HTTP header, UUID, or log text inside CLI Output, CLI Logs, CLI Headers, IDE Console, Live Tail, Problems, or API/Worker Response views.
   - The smart engine snaps to that semantic entity, highlights it cleanly without breaking list scrolling, and lets you either drag immediately upward into the Code Editor / URL inputs / external apps, or release to Expand, Shrink, Copy, or Drag.
5. **External App → EdgeCode Drop (Phase 4):**
   - Drag selected text from Chrome, Notes, Termux, or another app in multi-window mode over EdgeCode's Code Editor or any enabled input field (CLI path/body, API Tester URL/body, Worker Test, Find/Replace).
   - Valid drop surfaces illuminate with an accent drop border and live drop caret; releasing acquires and immediately releases Android drag permissions and inserts the text cleanly.

### Visual Identity & Theme

- **Aesthetic Direction:** Precision glassmorphic IDE craftsmanship aligned strictly with EdgeCode's existing **V1 (Structured Bordered)** and **V2 (Minimal OLED Translucent)** visual language.
- **Color Tokens & Hierarchy:** Built exclusively on `LocalIdePalette` and `MaterialTheme.colorScheme` tokens (`palette.glassElevated`, `palette.glassBorderHighlight`, `palette.activePillBg`, `palette.activePillFg`, `palette.ctaBg`, `palette.editorBg`, `palette.textPrimary`, `palette.textMuted`, plus `EmeraldLive` for Copy indicators and `palette.ctaBg` for Move/Drop caret).
- **Typography:** Monospace `JetBrainsMono` for scope chips, character counters, and dragged text preview pills; clean Material 3 sans-serif for toolbar labels and Settings controls, always scaled with `sp`.
- **Floating Drag Pill Shadow:** Custom native `DragShadowBuilder` rendering a frosted pill (`max 220dp` wide, matching V1/V2 radius, subtle border, compact Move/Copy glyph, single-line ellipsized monospace snippet + multi-line count badge) anchored **above** the fingertip touch point.
- **Settings → Editor → "Selection & Drag" Card (Phase 5):** Matches existing foldable Settings cards with an interactive mini preview reflecting the live Scope Badge, toolbar controls, and Move/Copy badges as options are toggled.

### Interactive Feedback & Motion

- **Haptics:** Distinct `LongPress` tick on smart capture, subtle ticks on progressive scope expansion and drop-target entry, and crisp confirmation tick on successful drop.
- **Animations:** Spring/tween reveals (`< 160ms`) for the floating Scope Pill, toolbar Expand/Shrink state changes, glowing drop-target field outline, and smooth gliding drop caret.
- **Accessibility:** All floating toolbar controls, Expand/Shrink buttons, drag grip, and Settings toggles meet `48dp` minimum touch target requirements (with generous hit zones around compact visual chips), clear `contentDescription` strings, and deterministic `testTag`s.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Pure-Kotlin Multi-Domain Structural Engine vs. Tree-sitter NDK
- **Chosen Approach:** Modular **Pure-Kotlin Smart Selection Engine** composed of domain strategies (`CodeStructureStrategy` for JS/TS/JSX, `JsonStructureStrategy` for JSON/JSONC, `MarkupStructureStrategy` for HTML/XML/CSS, and `TerminalSemanticStrategy` for CLI/logs/URLs/stack traces/headers) behind a clean parser-agnostic interface.
- **Why:** Works uniformly across formal code *and* CLI/terminal logs (where Tree-sitter has no log/URL/stack-frame grammars), avoids 6–12 MB native ABI bloat and Android 15/16 16 KB JNI hazards, runs in `< 1 ms` on a bounded `±32 KB` cursor window, tolerates broken syntax, and supports 100% deterministic JVM unit testing.
- **Alternatives Considered:** Third-party `android-tree-sitter` fork (rejected for native ABI/JVM-test/log-grammar limitations); naive regex word-only selection (rejected for failing on nested expressions, JSON pairs, and blocks).

### Decision 2: Gesture Disambiguation & Native Drag Initiation
- **Chosen Approach:** Retain the Code Editor's unified `awaitEachGesture` loop as the sole pointer arbiter; initiate Android Drag & Drop via `LocalView.current.startDragAndDrop(...)` with a custom `PillDragShadowBuilder`, typed in-process `EdgeDragPayload`, and `ClipData`; receive drops everywhere via Compose `Modifier.dragAndDropTarget` (verified for Compose BOM `2024.09.00` / Foundation `1.7.0`) with `positionInRoot()` root→local coordinate translation.
- **Why:** Prevents full-screen shadow distortion in Compose `1.7.0` `dragAndDropSource`, eliminates gesture conflicts with pinch-zoom/fling/handles/3-finger undo via strict priority order (**Teardrop Handles > Selection Body or Grip Drag > Hold Smart Select + Slide Drag > Scroll/Pinch/Undo/Tap**), and prevents toolbar/tab/sidebar offset skew on drop coordinates.

### Decision 3: Full Source × Destination Transfer Matrix & Safe Move Semantics
- **Chosen Approach:** Context-aware transfer defaults paired with explicit override controls:

| Source → Destination | Default Transfer | Edge-Case Protection |
|---|---|---|
| Editable Editor → **Same File** | **Move** (atomic 1-step Undo) | Drop inside `[srcStart..srcEnd]` is a no-op; forward drop adjusts target by `-movedLen`; target folded lines auto-unfold. |
| Editable Editor → **In-App Input / Other Pane** | **Copy** (non-destructive) | Protects source code when dragging URLs/tokens to CLI or API Tester; user can toggle Move if desired. |
| Read-Only CLI / Logs / Response → **Editor or Input** | **Copy** | Immutable source never modified; smart detector trims trailing punctuation cleanly. |
| Any In-App Surface → **External Android App** | **Copy** (`DRAG_FLAG_GLOBAL`) | Plain-text `ClipData` works across split-screen & freeform windows; cancel leaves source untouched. |
| External Android App → **Editor or Input** | **Insert / Drop** | Acquires `Activity.requestDragAndDropPermissions` inside `try / finally { release() }`; coerces plain text & HTML safely. |
| Any Surface → **Read-Only Surface** | **Rejected** | Drop target filter returns `false`; no misleading caret or highlight. |

### Decision 4: Atomic Compound Undo/Redo for Same-File Moves
- **Chosen Approach:** Add `EdgeEditorSession.moveRange(sourceStart, sourceEnd, destinationOffset)` backed by an atomic compound undo entry recording the remove+insert transition directly.
- **Why:** Uses $O(\text{movedLen})$ memory instead of snapshotting hundreds of lines between source and destination, keeps folds/bookmarks/incremental syntax styles synchronized, and lets a single Undo/Redo reverse or replay the entire move atomically.

### Decision 5: Granular Hold & Overlay Customization
- **Chosen Approach:** Persist `SelectionDragConfig` in `EditorSettings` (DataStore Preferences) and expose it in a dedicated **Selection & Drag** section inside **Settings → Editor**: Hold Mode (`Smart Hold + Toolbar`, `Progressive Hold`, `Classic Word`), Initial Auto-Select Depth (`Smart Auto`, `Compact`, `Broad`), Default Editor Drag Mode (`Smart`, `Always Copy`, `Always Move`), Immediate Slide-to-Drag toggle, Hold Delay (`240ms / 320ms / 450ms`), Cross-App Drag toggle, and all 7 Hold Overlay visibility switches (`Scope Badge`, `Expand/Shrink Buttons`, `Drag Grip`, `Move/Copy Switch`, `Drop Target Loupe`, `Quick Actions`, `Code Actions`).

---

## 4. Technical Architecture, Phased Execution Plan & Verification Gates *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                [Phase 5] Settings → Editor (Selection & Drag)                │
│  Hold Mode · Initial Depth · Transfer Policy · Hold Delay · Overlay Toggles  │
└───────────────────────────────────────┬──────────────────────────────────────┘
                                        │ persisted via DataStore (EditorSettings)
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│            [Phase 1] SmartTextSelectionEngine (Pure Kotlin Core)             │
│  ┌────────────────────┐ ┌──────────────────┐ ┌────────────────────────────┐  │
│  │ CodeStructure      │ │ Json / Markup    │ │ TerminalSemantic           │  │
│  │ (JS/TS/Expr/Block) │ │ (Pairs/Tags/Attr)│ │ (URL/Path/JSON/Hdr/CLI)    │  │
│  └─────────┬──────────┘ └────────┬─────────┘ └──────────────┬─────────────┘  │
│            └─────────────────────┼──────────────────────────┘                │
│                                  ▼                                           │
│       Deduplicated Scope Ladder: List<SelectionScope(range, kind, label)>    │
└──────────────────┬───────────────────────────────────────┬───────────────────┘
                   │                                       │
                   ▼                                       ▼
┌─────────────────────────────────────┐  ┌─────────────────────────────────────┐
│ [Phase 2 + 5] Code Editor Surface   │  │ [Phase 3] CLI / Logs & Input Fields │
│ • Unified Pointer Gesture Arbiter   │  │ • SmartDraggableText (CLI Output,   │
│ • Smart Scope Pill & Custom Toolbar │  │   CLI Logs/Headers, IDE Console,    │
│ • Drop Caret + Ghost Source         │  │   Live Tail, Problems, Responses)   │
│ • Edge Auto-Scroll Frame Ticker     │  │ • EdgeTextDropTarget Modifier       │
│ • Atomic Compound Move Undo/Redo    │  │   (CLI Path/Body, API Tester,       │
│ • [Phase 5] Progressive Hold + Loupe│  │   Worker Test, Find & Replace)      │
└──────────────────┬──────────────────┘  └──────────────────┬──────────────────┘
                   │                                        │
                   └───────────────────┬────────────────────┘
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│       [Phase 2 + 4] EdgeTextDragCoordinator (Android Framework Bridge)       │
│  • Launch: View.startDragAndDrop(ClipData, CustomPillShadow, Payload, Flags) │
│  • Receive: Compose Modifier.dragAndDropTarget + Root→Local Coordinate Map   │
│  • [Phase 4] Cross-App: DRAG_FLAG_GLOBAL + requestDragAndDropPermissions     │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Gesture & Hold State Machine

```
[IDLE] ──(Touch Down)──► Check Teardrop Handle? ──(Yes)──► [HANDLE_DRAG + LOUPE]
   │
   ├──(2 Fingers)──► [PINCH_ZOOM]         ├──(3 Fingers)──► [SWIPE_UNDO_REDO]
   │
   ├──(Move > slop before hold)──► [SCROLL_2D + FLING]
   │
   ├──(Tap Up before hold)──► [TAP / DOUBLE-TAP / TRIPLE-TAP]
   │
   └──(Hold Delay Reached)──► SmartTextSelectionEngine.resolveAt(offset)
            │
            ├──[Phase 5: Still + Progressive Mode]──► Step Up Ladder (+450ms)
            │
            ├──[Finger Lifts]──► [SELECTED + CUSTOMIZABLE TOOLBAR & HANDLES]
            │                        └──(Hold Body or Toolbar Drag Grip)──┐
            │                                                             ▼
            └──[Finger Moves > slop]──────────────────────────► [SYSTEM_DRAG_ACTIVE]
                                                                  │   • Pill Shadow
                                                                  │   • Drop Caret (+Loupe)
                                                                  │   • Edge Auto-Scroll
                                                                  ▼
                                                    [DROP / CANCEL] ──► Atomic Move or Copy
```

### Strict 5-Phase Gated Build & Verification Sequence

```
[Phase 1: Engine + Tests] ──(Compile + Unit Tests PASS)──►
[Phase 2: Editor Hold, Same-File Move & Atomic Undo] ──(Compile + Unit Tests PASS)──►
[Phase 3: CLI/Log Smart Drag & In-App Input Drops] ──(Compile + Unit Tests PASS)──►
[Phase 4: Cross-App Global Drag & External Permissions] ──(Compile + Unit Tests PASS)──►
[Phase 5: Settings, Progressive Hold & Drop Loupe] ──(Debug + Release Compile + Full Tests PASS)
```

1. **Phase 1 — Smart Selection Engine + Unit Tests**
   - *Deliverables:* `SmartTextSelectionEngine`, `SelectionScope` / `SelectionLadderState`, bounded-window lexical scanner, `CodeStructureStrategy` (JS/TS/template literals/member chains/calls/statements/blocks), `JsonStructureStrategy` (key-value pairs/arrays/objects), `MarkupStructureStrategy` (HTML/JSX tags & attributes), and `TerminalSemanticStrategy` (URLs with trailing punctuation trim, stack-trace `file:line:col`, inline JSON, HTTP headers, UUIDs/IP:port/emails, CLI flags/commands).
   - *Mandatory Gate 1:* Build succeeds (`compile_applet`) AND dedicated engine JVM unit test suite passes 100% across all code, broken-syntax, JSON, markup, and terminal/log edge cases before starting Phase 2.
2. **Phase 2 — Code Editor Smart Hold + Same-File Move + Atomic Compound Undo/Redo**
   - *Deliverables:* Compound atomic `UndoEntry` + `EdgeEditorSession.moveRange(srcStart, srcEnd, dstOffset)` (with forward/backward offset math, self-range no-op, fold/bookmark updates); `EdgeTextDragCoordinator` + custom floating `PillDragShadowBuilder`; unified `EdgeCodeEditorSurface` gesture arbiter (smart hold + one-motion slide-drag + selection-body drag + toolbar Drag Grip); upgraded `EdgeSelectionToolbar` (`Scope Badge`, `Expand +`, `Shrink −`, `Drag Grip`, `Move ⇄ Copy`); canvas drop caret, dashed ghost source highlight, root→local coordinate conversion, and `withFrameNanos` edge auto-scroll loop.
   - *Mandatory Gate 2:* Build succeeds AND new `EdgeEditorSession` move/undo/redo unit tests (forward move, backward move, drop inside own selection no-op, start/end boundaries, multi-line/folded moves, multi-step Undo/Redo invariance) pass 100% before starting Phase 3.
3. **Phase 3 — CLI / Terminal / Log Surfaces & In-App Input Drop Targets**
   - *Deliverables:* Reusable `SmartDraggableText` wired into CLI Output, CLI Error, CLI Logs, CLI Headers, IDE Console, Live Tail, Problems, and API/Worker response bodies (using `TextLayoutResult` hit-testing without disrupting list scrolling, with contextual Scope/Expand/Shrink/Copy/Drag overlay); reusable `Modifier.edgeTextDropTarget` with hover border highlight wired into CLI Path/Body, Editor Find & Replace, API Tester URL/Headers/Body, and Worker Test URL/Headers/Body.
   - *Mandatory Gate 3:* Build succeeds AND all unit tests pass before starting Phase 4.
4. **Phase 4 — Cross-App Android Drag & Drop**
   - *Deliverables:* `View.DRAG_FLAG_GLOBAL` cross-app drag emission across all drag sources; full external-app drop ingestion on the Code Editor and every `edgeTextDropTarget` field using `Activity.requestDragAndDropPermissions(event.toAndroidDragEvent())` inside `try / finally { release() }`, multi-item `ClipData` extraction, safe HTML-to-plain-text coercion, and strict MIME filtering.
   - *Mandatory Gate 4:* Build succeeds AND unit tests covering external `ClipData` extraction, HTML coercion, empty/unsupported MIME rejection, and permission lifecycle pass 100% before starting Phase 5.
5. **Phase 5 — Settings Customization, Progressive Hold & Drop Target Loupe**
   - *Deliverables:* `SelectionDragConfig` model + DataStore persistence inside `EditorSettings`; new foldable **"Selection & Drag"** section in **Settings → Editor** with live interactive preview and every hold-behavior & overlay-visibility control; **Progressive Hold-to-Expand** step timer (`+450ms` rungs with haptics + live Scope Pill); **`1.5x` Drop Target Glass Loupe** rendered above the fingertip during drag-over aiming inside the Code Editor.
   - *Mandatory Final Gate 5:* `SelectionDragConfig` round-trip persistence tests + full `gradle :app:testDebugUnitTest` + `compile_applet` + `gradle :app:compileReleaseKotlin --rerun --no-build-cache` (GitHub Actions release parity) all pass cleanly.

### Data Model & State

- **`SelectionScope`**: Immutable rung on the ladder (`range: IntRange`, `kind: SelectionScopeKind`, `label: String`).
- **`SelectionLadderState`**: Computed `scopes: List<SelectionScope>`, `activeIndex: Int`, and anchor offset for exact Expand/Shrink traversal.
- **`SelectionDragConfig`**: Persisted in `EditorSettings` (DataStore) controlling Hold Mode, Initial Depth, Transfer Policy, Hold Delay, Immediate Slide-to-Drag, Cross-App Global Drag, and all 7 Hold Overlay visibility toggles.
- **`EdgeDragPayload`**: In-process drag session metadata (`dragId`, `sourceSurfaceId`, `sourceEditable`, `sourceRange`, `text`, `effectiveMode: MOVE | COPY`, `scopeLabel`) paired with standard plain-text `ClipData`.
- **`DragDropVisualState`**: Canvas-local hover coordinates, resolved drop document offset, self-source flag, and active Move/Copy mode read directly in the draw phase for zero-jank rendering and auto-scrolling.
