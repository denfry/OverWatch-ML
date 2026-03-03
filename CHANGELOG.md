# Changelog

All notable changes to OverWatch-ML will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - 2026-03-03

### Fixed
- **Critical Thread Safety**: Fixed Bukkit API calls in `BotTrainingManager` from async threads that could cause server crashes.
- **Bot Performance**: Reduced bot ore search radius by 90% (from 1331 blocks to 125) to save Main Thread performance.
- **Lag Spikes**: Moved `PunishmentManager` file saving and IP-ban configuration updates to an asynchronous executor to prevent server freezing.

### Optimized
- **Vanilla Performance**: Throttled advanced ML movement analysis in `AdvancedDetectionEngine` to once per second per player.
- **Chunk Loading Protection**: Added `isChunkLoaded` checks to `DecoyManager` validation to prevent synchronous chunk loading lag spikes.
- **Resource Management**: Optimized default background task intervals in `config.yml` (telemetry, ML tracking, and monitoring) to reduce overhead on survival servers.

## [2.0.0] - 2026-02-28

### Features
- **Three-Tier ML Pipeline**: Advanced multi-stage detection balancing performance and accuracy.
- **Combat Cheat Detection**: New independent system for detecting kill-aura, reach, and other combat hacks.
- **Modern GUI Framework**: A completely new, responsive, and intuitive interface for staff.
- **Online Learning**: ML models now learn continuously from player behavior without server restarts.
- **Behavioral Profiling**: Deep analysis of player actions over time with persistence.
- **Context-Aware Heuristics**: Smart filtering that understands server state and environmental factors.
- **Adaptive Punishment System**: ML-powered recommendations that adapt to server history.
- **Data Optimization Suite**: High-performance data processing with minimal storage footprint.
- **Java 21 Virtual Threads**: Migrated `AsyncExecutor` to use virtual threads for high-concurrency I/O and light tasks.
- **Correlation Engine**: New system for cross-provider anti-cheat consensus (Grim, Vulcan, Matrix, Spartan).
- **Batch Audit Logging**: High-performance asynchronous logging with virtual threads.
- **Modern Text API**: Full support for MiniMessage and Adventure API across the entire plugin.

### Improvements
- **Performance**: Significant optimizations in Tier 1 processing.
- **Java 21 Records**: Optimized `DetectionResult` and other DTOs using record classes for better performance.
- **Security**: Enhanced data encryption and secure webhook communications.
- **User Experience**: Completely rewritten command system with interactive help and validation.
- **Integration**: Full support for Minecraft 1.21.x and ProtocolLib features.
