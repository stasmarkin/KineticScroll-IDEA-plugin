# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KineticScroll is an IntelliJ IDEA plugin that provides mobile-like kinetic scrolling with inertia effects. The plugin captures middle-button mouse drags and applies physics-based momentum to scrolling in editors, scroll panes, and terminals.

## Commands

### Build and Development
- `./gradlew build` - Build the plugin
- `./gradlew runIde` - Run IntelliJ IDEA with the plugin in a sandbox environment
- `./gradlew buildPlugin` - Create distributable plugin ZIP
- `./gradlew publishPlugin` - Publish to JetBrains marketplace (requires PUBLISH_TOKEN system property)

### Testing and Verification
- `./gradlew verifyPlugin` - Verify plugin compatibility
- `./gradlew runPluginVerifier` - Run compatibility verification against IDE versions

## Architecture

### Core Components

**KineticScrollEventListener** (`src/main/kotlin/KineticScroll.kt:31`)
- Main event dispatcher implementing both `IdeEventQueue.EventDispatcher` and `AWTEventListener`
- Dual-level event handling:
  - `AWTEventListener` intercepts terminal events at system level before component handlers
  - `IdeEventQueue.EventDispatcher` handles non-terminal components (editors, scroll panes)
- Intercepts mouse events and determines when to activate kinetic scrolling
- Manages Handler lifecycle for different component types

**Handler Classes** (`src/main/kotlin/KineticScroll.kt:298`)
- Abstract base class with three concrete implementations:
  - `EditorHandler` - Handles scrolling in code editors using `EditorEx.scrollingModel`
  - `ScrollPaneHandler` - Handles scrolling in general scroll panes using scrollbar values
  - `TerminalHandler` - Handles scrolling in terminal widgets with multiple fallback approaches (JScrollPane parent, reflection-based terminal panel access, synthetic MouseWheelEvent dispatch)
- Manages velocity tracking, inertia calculations, and cursor state
- Auto-disposes on window focus loss or component visibility changes

**Trail Algorithms** (`src/main/kotlin/TrailAlgorithms.kt`)
- `TrailMovement` interface defines inertia behavior contracts
- `ExponentialSlowdownTrail` - Physics-based exponential decay with subpixel rendering (default)
- `LinearSlowdownTrail` - Linear velocity reduction
- `VerticalTrail` wrapper restricts scrolling to vertical axis only
- `NoTrail` disables inertia completely

**Settings System** (`src/main/kotlin/FMSSettings.kt:17`)
- `FMSSettings` - Persistent application-level service storing user preferences
- `FMSConfigurable` - UI configuration panel for IDE settings (Appearance & Behavior section)
- Configurable parameters: FPS, activation time, decay algorithms, throwing sensitivity, subpixel trail

### Key Algorithms

**Velocity Calculation** (`src/main/kotlin/KineticScroll.kt:373`)
Uses weighted average of recent mouse movements within a time window to determine throwing velocity. More recent movements have higher weight using quadratic weighting formula.

**Inertia Physics** (`src/main/kotlin/TrailAlgorithms.kt`)
- Exponential: `v(t) = v0 / 2^(t/p)` with integrated distance calculation
- Linear: `v(t) = v0 - p*t` with quadratic distance function
- Subpixel shift tracking prevents pixel rounding errors in smooth scrolling

### Event Flow

1. Mouse button press → Identify component (Editor/Terminal/ScrollPane) → Install appropriate Handler
2. Mouse drag → Update velocity with weighted averaging → Scroll component directly
3. Mouse release → Calculate final velocity → Create Trail → Apply inertia with physics simulation
4. Auto-dispose on focus loss, visibility change, or other mouse button press

### Plugin Registration

**plugin.xml** (`src/main/resources/META-INF/plugin.xml`)
- Registers both `ideEventQueueDispatcher` and `applicationService` for event interception
- Defines settings configurable in Appearance & Behavior section
- Maps default middle-button mouse shortcut (`button2`) to kinetic scroll action
- Customizable shortcut via Settings | Keymap | Plugins | Kinetic Mouse Scrolling

## Build Configuration

- **Kotlin version**: 1.9.22
- **IntelliJ Platform**: 2023.3 (IC - Community Edition)
- **Target JVM**: Java 17
- **Gradle IntelliJ Plugin**: 1.17.1
- **Plugin ID**: com.stasmarkin.kineticscroll
- **Current Version**: 1.0.5-SNAPSHOT
- **Min IDE build**: 241

## Development Notes

- Plugin uses some deprecated APIs that may need updates for newer IntelliJ versions
- Physics calculations run at configurable FPS (default 60) using `Alarm` scheduler
- Subpixel rendering supported for smooth exponential decay trails
- Terminal support uses AWTEventListener to intercept events before terminal's native middle-click handler
- Window focus and component visibility changes automatically dispose active handlers
- Three-tiered fallback approach for terminal scrolling ensures compatibility across different terminal implementations
