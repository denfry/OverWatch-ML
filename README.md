# OverWatch-ML

**Intelligent Cheat Detection Powered by Behavior**

![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Java](https://img.shields.io/badge/Java-21+-red.svg)
![Paper](https://img.shields.io/badge/Paper-1.21-orange.svg)

## Overview

OverWatch-ML is a next-generation cheat detection system designed for modern Minecraft servers. Traditional anti-cheats rely on simple heuristics and threshold checks, which are easily bypassed by sophisticated clients. OverWatch-ML takes a completely different approach by analyzing player behavior over time and applying advanced machine learning algorithms to distinguish between legitimate players and cheaters.

By continuously learning from your specific server's environment, OverWatch-ML adapts to your unique gameplay style and economy. It doesn't just block x-rayers; it profiles combat behavior, tracks movement patterns, and identifies anomalies that no human moderator could spot.

## Features

*   **💎 Xray Detection:** Identifies hidden ore mining patterns and unnatural block interaction behavior.
*   **⚔️ Combat Cheat Detection:** Advanced heuristics for identifying kill-aura, reach, and aim-assist modifications.
*   **🧠 Three-Tier ML Pipeline:** A sophisticated, tiered approach to detection, balancing performance and accuracy.
*   **👤 Behavioral Profiling:** Builds long-term profiles for players to detect sudden changes in behavior.
*   **📈 Server-Adaptive Learning:** Automatically adjusts detection thresholds based on your server's average player base.
*   **🖥️ Staff GUI:** A comprehensive, modern, and intuitive graphical interface for moderators and administrators.
*   **🚀 Zero External ML Dependencies:** Completely self-contained machine learning engine; no external Python scripts or APIs required.
*   **💬 Discord Integration:** Seamlessly integrates with your Discord server for real-time alerts and logs.

## How It Works

### Tier 1: Real-Time Heuristics
The first tier operates continuously, evaluating player actions in real-time with negligible performance impact. It filters out obvious legitimate behavior and flags suspicious activities for further analysis. This tier ensures that the server remains lag-free while still capturing essential data.

### Tier 2: Behavioral Analysis
When Tier 1 flags a player, Tier 2 activates to build a short-term behavioral profile. It analyzes sequences of actions, interaction timings, and movement paths to identify patterns common to known cheat clients. This tier operates asynchronously, ensuring smooth server performance.

### Tier 3: Deep Machine Learning
For the most complex cases, Tier 3 employs advanced Machine Learning algorithms, including Isolation Forests and Autoencoders. It compares the player's long-term behavior against vast datasets of both legitimate and cheating players to make a highly accurate final determination.

## Requirements

| Requirement | Specification |
| :--- | :--- |
| **Minecraft Server** | Paper 1.16.5+ (1.21 Recommended) |
| **Java Version** | Java 21+ |
| **Memory (RAM)** | 4GB Minimum, 8GB Recommended |
| **Dependencies** | ProtocolLib (Latest Version) - **Required** |

## Installation

1.  Ensure your server is running a compatible version of Paper and Java 21.
2.  Install the latest version of **ProtocolLib** in your `plugins` folder.
3.  Download the latest release of `OverWatch-ML.jar` and place it in your `plugins` folder.
4.  Restart your server to generate the default configuration files.
5.  Configure the plugin via the `plugins/OverWatch-ML/config.yml` file or in-game GUI, then run `/owml reload`.

## Building from Source

To compile the plugin yourself, clone the repository and build it using Maven:

```bash
mvn clean package
```
The compiled jar will be located in the `target/` directory.

## Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/overwatch` (or `/owml`) | Advanced AntiXray command with ML-powered subcommands and interactive help. | `owml.use` |

## ML System

OverWatch-ML utilizes a highly optimized three-tier machine learning pipeline designed specifically for the Minecraft server environment. The system employs **Isolation Forest** algorithms for anomaly detection and **Autoencoders** to reconstruct typical player behavior patterns. The core of the system relies on a multi-dimensional **feature vector** that captures intricate details about player actions, allowing the models to calculate precise anomaly metrics without relying on external APIs.

## Performance

OverWatch-ML is engineered for minimal impact on server performance.

| Metric | Impact |
| :--- | :--- |
| **TPS Impact** | < 0.5% |
| **Memory Usage** | 50–200MB |
| **Tier 1 Processing** | < 1ms |
| **Tier 2 Processing** | 5–20ms |
| **Tier 3 Processing** | Once every 5 minutes |

## Contributing

We welcome contributions! If you're interested in improving the ML models, expanding the GUI, or fixing bugs, please check out our [CONTRIBUTING.md](CONTRIBUTING.md) guide.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
