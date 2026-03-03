# 🛡️ OverWatch-ML
**Next-Generation Machine Learning powered Anti-Cheat for Minecraft 1.21+**

OverWatch-ML is a sophisticated security solution that combines traditional heuristic checks with advanced Behavioral Analysis and Machine Learning to protect your server from complex cheats that traditional anti-cheats often miss.

## ✨ Key Features

*   **🧠 ML-Driven Detection:** Dedicated models for **Xray**, **Combat**, **Movement**, and **World Interactions**. Our system learns from player behavior patterns rather than just looking at individual packets.
*   **📡 Detection Orchestrator:** A centralized "brain" that coordinates internal ML analysis with data from external providers like Grim, Vulcan, Matrix, and Spartan.
*   **📊 Real-time Behavioral Profiling:** Every player has a persistent behavioral profile. View a live stream of their actions (CPS, movement speed, mining efficiency) directly in the GUI.
*   **💻 Modern Staff Interface:** A stunning, intuitive GUI management system. Monitor suspicious players, review live alerts, and manage punishments without ever touching a config file.
*   **🛡️ Smart Bypasses:** Built-in protection against false positives for players in Creative and Spectator modes.
*   **🔔 Interactive Staff Alerts:** Real-time notifications for staff members with one-click teleportation to the suspect.
*   **⚡ High Performance:** Built on Java 21 and optimized for Paper 1.21+. All heavy ML computations run asynchronously to ensure zero impact on your server's TPS.

## 🚀 Getting Started

### Requirements
*   **Minecraft Version:** 1.21 or newer (Paper/Spigot)
*   **Java Version:** 21+
*   **Dependency:** [ProtocolLib](https://modrinth.com/plugin/protocollib)

### Commands
*   `/owml` - Open the central Staff Control Panel.
*   `/owml player <name>` - View a specific player's detailed ML profile.
*   `/owml staff` - Explicitly open the staff management menu.

### Permissions
*   `owml.staff` - Access to the staff menu and live alerts.
*   `owml.admin` - Full access to configuration and ML system tools.

## 🤝 Integrations
OverWatch-ML works best when paired with packet-based anti-cheats. It currently supports cross-referencing scores from:
*   **GrimAC**
*   **Vulcan**
*   **Matrix**
*   **Spartan**

---
*Developed with ❤️ by the OverWatch Team. Powered by Machine Learning.*
