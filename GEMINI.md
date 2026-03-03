# OverWatch-ML — Project Context for Gemini CLI

## Project Overview

This is a Minecraft Paper/Spigot plugin written in Java 21+.
Plugin name: OverWatch-ML. Commands: /owml, /overwatch.
Permissions prefix: owml. Package structure follows existing
conventions in src/main/java. Build system: Maven (pom.xml).
Target server: Paper 1.16.5+, optimized for 1.21+.
Required dependency: ProtocolLib.

## Critical Rules — Read Before Every Task

Always read the relevant existing files before writing any code.
Never assume class names, package names, method signatures or field
names — always verify by reading the actual file first.
Never delete or overwrite existing functionality unless explicitly
asked. When adding to an existing class show only the changed
parts as a diff, not the entire class rewritten.
If a class already exists do not create a duplicate with a similar
name. Check with glob or search before creating anything new.
Always confirm the build passes with mvn clean package before
reporting a task as complete. If the build fails fix all errors
before stopping.

## Java and Paper API Rules

Java version is 21. Use modern Java features: records, sealed
classes, pattern matching, text blocks where appropriate.
Never use deprecated Paper or Bukkit API methods. Always check
that Sound constants, Material constants and other enums exist
in Paper 1.21 API before using them.
Never call Bukkit API from async threads except methods explicitly
documented as thread-safe. Use BukkitScheduler.runTask to return
to main thread. Use BukkitScheduler.runTaskAsynchronously for
heavy operations.
Never use System.out.println. Use the plugin Logger obtained
via getLogger() for all console output.
Never hardcode the plugin folder path. Always use
getDataFolder() from the plugin instance.
Always handle ClassNotFoundException and NoClassDefFoundError
when loading optional integrations with external plugins.
External plugin integrations must work even when the external
plugin is absent. Use Bukkit.getPluginManager().getPlugin()
to check availability before any class loading.

## Architecture Rules

The plugin has two independent ML detection systems: XrayDetection
and CombatDetection. They share PlayerBehaviorProfile and
PlayerEventBuffer but have separate model instances.
DetectionOrchestrator coordinates both systems and external
integrations. Never bypass it to call detection systems directly.
All ML models implement DetectionModel interface. All GUI screens
implement OverWatchGUI interface. All external antichat
integrations implement AntiCheatIntegration interface.
BehaviorProfileManager is the single source of truth for player
profiles. Never store player data in static fields or other maps
outside of it.
GUINavigationStack manages GUI history for back button support.
Always push to the stack when opening a GUI and pop when going back.

## Code Style

Package naming: use the existing groupId from pom.xml as prefix.
Class names: PascalCase. Method names: camelCase. Constants:
UPPER_SNAKE_CASE. Fields: camelCase with no Hungarian notation.
All public methods and classes must have Javadoc comments in English.
Maximum line length is 120 characters.
Always use @Override annotation when overriding methods.
Prefer final fields and local variables where possible.
Use Optional instead of returning null from methods.
Never use raw types. Always parameterize generics fully.

## Thread Safety Rules

All per-player state lives in ConcurrentHashMap. Never use
HashMap for shared mutable state accessed from multiple threads.
ML model reads use ReadWriteLock.readLock. ML model writes use
ReadWriteLock.writeLock. Always acquire and release locks in
try-finally blocks.
RingBuffer is synchronized. PlayerEventBuffer is thread-safe
through ConcurrentHashMap and synchronized RingBuffer.
ScheduledExecutorService in DetectionOrchestrator has pool
size of 2 threads maximum to avoid overloading the server.
Always cancel scheduled tasks and shut down executors in
the plugin onDisable method.

## Maven and Dependencies Rules

Never add a new dependency without checking it is available
in Maven Central or a configured repository in pom.xml.
External antichat plugin APIs (Vulcan, Grim, Matrix, Spartan)
must be added with scope provided or system so they are not
bundled in the final jar.
Always run mvn clean package after adding dependencies to
verify they resolve correctly.
Never bundle Paper API or ProtocolLib in the shaded jar.
They must remain as provided scope.

## File and Data Rules

All persistent data saves to getDataFolder() only.
Player profiles serialize to JSON via Gson and save to
a profiles subdirectory inside the plugin data folder.
ML model weights serialize to JSON and save to a models
subdirectory inside the plugin data folder.
Always create a migrated.lock file after successful migration
from old data format to prevent duplicate migration on restart.
Never save data on the main thread. Always use async tasks
for file I/O.

## GUI Rules

Every GUI class implements OverWatchGUI with open, close,
refresh and handleClick methods.
Every GUI that opens another GUI must push itself to
GUINavigationStack first and the new GUI must have a back
button that calls GUINavigationStack.pop.
All ItemStack creation goes through ItemBuilder. Never call
new ItemStack and manually set meta directly in GUI classes.
All lore strings must go through LoreFormatter to enforce
40 character line limit and consistent color.
Auto-refresh GUIs through GUIManager scheduled task, not
through individual tasks per player. The GUIManager handles
refresh for all open GUIs every 5 seconds.
Never play sounds or show BossBar on the async thread.
Use BukkitScheduler.runTask to call GUIEffects methods
from the main thread.

## Common Mistakes to Avoid

Do not create a new ScheduledExecutorService inside a GUI class
or listener. Only DetectionOrchestrator owns an executor.
Do not register event listeners inside constructors. Always
register through PluginManager in the main plugin class or
in a dedicated setup method called from onEnable.
Do not call player.getLocation() or any world method from
an async thread without synchronizing back to main thread.
Do not use Thread.sleep anywhere. Use BukkitScheduler for delays.
Do not catch Exception or Throwable broadly and silently ignore.
Always log the error with getLogger().severe() and include the
stack trace via e.printStackTrace() or logger.log(Level.SEVERE).
Do not assume a player is online when processing async results.
Always check Bukkit.getPlayer(uuid) is not null before any
player interaction in async callbacks.
Do not store Player references in long-lived objects. Store UUID
and look up the Player when needed.
Do not access plugin config from async threads. Cache config
values in MLConfig object on the main thread at startup and
reload time.

## Task Workflow

When given a task always follow this sequence:
First read the existing relevant files to understand current state.
Second plan the changes and list files that will be created or modified.
Third implement the changes one file at a time confirming each.
Fourth run mvn clean package and show the full output.
Fifth fix any compilation errors completely before reporting done.
Sixth list all created and modified files as a summary.

When asked to modify an existing class show a minimal diff
not the entire class rewritten unless the class is small
under 50 lines or a full rewrite was explicitly requested.

When asked to create a new class always check first that
a class with a similar name or purpose does not already exist
somewhere in the project.

## Integration Priority

When CorrelationEngine combines internal and external scores
the formula is: combinedScore = internalScore * 0.6 plus
externalCorrelationScore * 0.4. If getCrossProviderConsensus
returns 2 or more multiply combinedScore by 1.3 capped at 1.0.
Provider weights: Grim 1.4 for movement 1.2 otherwise, Vulcan
1.3 for combat 1.1 otherwise, Matrix 1.3 for AutoClicker
1.1 otherwise, Spartan 1.2 for scaffold 1.0 otherwise.
These weights must not be changed without updating this file.

## Build Environment

Maven is installed at C:\Users\dabin\Documents\apache-maven-3.9.12\bin
Always use this exact path when running Maven commands via shell.
Never assume mvn is available in PATH directly. Always call it as:
C:\Users\dabin\Documents\apache-maven-3.9.12\bin\mvn

Example build command:
C:\Users\dabin\Documents\apache-maven-3.9.12\bin\mvn clean package

Example build skipping tests:
C:\Users\dabin\Documents\apache-maven-3.9.12\bin\mvn clean package -DskipTests

When verifying compilation after any code change always run the
full build command with the absolute path above and show the
complete output including any errors.
