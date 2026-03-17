package net.denfry.owml.plugins;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Plugin manager for OverWatchML extensions.
 * Allows loading custom detectors and modules dynamically.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class PluginManager {

    private final OverWatchML plugin;
    private final String pluginsDir;

    // Loaded plugins
    private final Map<String, OverWatchPlugin> loadedPlugins = new ConcurrentHashMap<>();

    // Class loaders for plugins
    private final Map<String, URLClassLoader> pluginClassLoaders = new ConcurrentHashMap<>();

    // Plugin event listeners
    private final List<Listener> pluginListeners = new ArrayList<>();

    public PluginManager() {
        this.plugin = OverWatchML.getInstance();
        this.pluginsDir = new File(plugin.getDataFolder(), "extensions").getPath();
        createPluginsDirectory();
        loadPlugins();
    }

    /**
     * Load all plugins from the extensions directory
     */
    public void loadPlugins() {
        try {
            Path pluginsPath = Paths.get(pluginsDir);
            if (!Files.exists(pluginsPath)) {
                return;
            }

            Files.walk(pluginsPath)
                .filter(path -> path.toString().endsWith(".jar"))
                .forEach(this::loadPluginJar);

            MessageManager.log("info", "Loaded {COUNT} OverWatch plugins",
                "COUNT", String.valueOf(loadedPlugins.size()));

        } catch (IOException e) {
            MessageManager.log("error", "Failed to load plugins: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Load a single plugin JAR file
     */
    private void loadPluginJar(Path jarPath) {
        try {
            String pluginName = jarPath.getFileName().toString().replace(".jar", "");
            URL jarUrl = jarPath.toUri().toURL();

            // Create class loader for this plugin
            URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl},
                plugin.getClass().getClassLoader());

            // Find the main plugin class
            String mainClass = findMainPluginClass(jarPath);
            if (mainClass == null) {
                MessageManager.log("warning", "No main plugin class found in {PLUGIN}", "PLUGIN", pluginName);
                return;
            }

            // Load the plugin class
            Class<?> pluginClass = classLoader.loadClass(mainClass);
            if (!OverWatchPlugin.class.isAssignableFrom(pluginClass)) {
                MessageManager.log("warning", "Plugin class {CLASS} does not implement OverWatchPlugin",
                    "CLASS", mainClass);
                return;
            }

            // Create plugin instance
            Constructor<?> constructor = pluginClass.getConstructor();
            OverWatchPlugin pluginInstance = (OverWatchPlugin) constructor.newInstance();

            // Initialize plugin
            PluginContext context = new PluginContext(plugin, pluginName, this);
            pluginInstance.initialize(context);

            // Register plugin
            loadedPlugins.put(pluginName, pluginInstance);
            pluginClassLoaders.put(pluginName, classLoader);

            // Register event listeners if any
            if (pluginInstance instanceof Listener) {
                pluginListeners.add((Listener) pluginInstance);
                Bukkit.getPluginManager().registerEvents((Listener) pluginInstance, plugin);
            }

            MessageManager.log("info", "Loaded plugin: {PLUGIN} ({CLASS})",
                "PLUGIN", pluginName, "CLASS", mainClass);

        } catch (Exception e) {
            MessageManager.log("error", "Failed to load plugin {JAR}: {ERROR}",
                "JAR", jarPath.getFileName().toString(), "ERROR", e.getMessage());
        }
    }

    /**
     * Find the main plugin class in a JAR file
     */
    private String findMainPluginClass(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            URL jarUrl = jarPath.toUri().toURL();
            try (URLClassLoader tempLoader = new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader())) {
                Enumeration<JarEntry> entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (name.endsWith(".class") && !name.contains("$")) {
                        String className = name.replace(".class", "").replace("/", ".");

                        // Check if this class extends OverWatchPlugin
                        try {
                            Class<?> clazz = Class.forName(className, false, tempLoader);
                            if (OverWatchPlugin.class.isAssignableFrom(clazz)) {
                                return className;
                            }
                        } catch (ClassNotFoundException | NoClassDefFoundError e) {
                            // Continue searching
                        }
                    }
                }
            }
        } catch (IOException e) {
            MessageManager.log("error", "Failed to read JAR file: {ERROR}", "ERROR", e.getMessage());
        }

        return null;
    }

    /**
     * Reload a specific plugin
     */
    public boolean reloadPlugin(String pluginName) {
        try {
            // Unload plugin
            unloadPlugin(pluginName);

            // Find and reload JAR
            Path pluginsPath = Paths.get(pluginsDir);
            Path jarPath = pluginsPath.resolve(pluginName + ".jar");

            if (Files.exists(jarPath)) {
                loadPluginJar(jarPath);
                return true;
            } else {
                MessageManager.log("warning", "Plugin JAR not found: {PLUGIN}", "PLUGIN", pluginName);
                return false;
            }

        } catch (Exception e) {
            MessageManager.log("error", "Failed to reload plugin {PLUGIN}: {ERROR}",
                "PLUGIN", pluginName, "ERROR", e.getMessage());
            return false;
        }
    }

    /**
     * Unload a plugin
     */
    public boolean unloadPlugin(String pluginName) {
        OverWatchPlugin pluginInstance = loadedPlugins.remove(pluginName);
        if (pluginInstance == null) {
            return false;
        }

        try {
            // Call shutdown method if available
            Method shutdownMethod = pluginInstance.getClass().getMethod("shutdown");
            shutdownMethod.invoke(pluginInstance);

        } catch (Exception e) {
            // Shutdown method not available or failed
        }

        // Remove from listeners
        pluginListeners.removeIf(listener -> listener == pluginInstance);

        // Close class loader
        URLClassLoader classLoader = pluginClassLoaders.remove(pluginName);
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                MessageManager.log("warning", "Failed to close class loader for {PLUGIN}: {ERROR}",
                    "PLUGIN", pluginName, "ERROR", e.getMessage());
            }
        }

        MessageManager.log("info", "Unloaded plugin: {PLUGIN}", "PLUGIN", pluginName);
        return true;
    }

    /**
     * Get a loaded plugin by name
     */
    public OverWatchPlugin getPlugin(String name) {
        return loadedPlugins.get(name);
    }

    /**
     * Get all loaded plugin names
     */
    public Set<String> getLoadedPlugins() {
        return new HashSet<>(loadedPlugins.keySet());
    }

    /**
     * Check if a plugin is loaded
     */
    public boolean isPluginLoaded(String name) {
        return loadedPlugins.containsKey(name);
    }

    /**
     * Get plugin information
     */
    public PluginInfo getPluginInfo(String name) {
        OverWatchPlugin pluginInstance = loadedPlugins.get(name);
        if (pluginInstance == null) {
            return null;
        }

        return new PluginInfo(
            name,
            pluginInstance.getVersion(),
            pluginInstance.getDescription(),
            pluginInstance.getAuthor(),
            pluginInstance.isEnabled()
        );
    }

    /**
     * Enable or disable a plugin
     */
    public boolean setPluginEnabled(String name, boolean enabled) {
        OverWatchPlugin pluginInstance = loadedPlugins.get(name);
        if (pluginInstance == null) {
            return false;
        }

        try {
            if (enabled) {
                pluginInstance.enable();
            } else {
                pluginInstance.disable();
            }
            return true;
        } catch (Exception e) {
            MessageManager.log("error", "Failed to {ACTION} plugin {PLUGIN}: {ERROR}",
                "ACTION", enabled ? "enable" : "disable", "PLUGIN", name, "ERROR", e.getMessage());
            return false;
        }
    }

    /**
     * Create plugins directory if it doesn't exist
     */
    private void createPluginsDirectory() {
        try {
            Path pluginsPath = Paths.get(pluginsDir);
            if (!Files.exists(pluginsPath)) {
                Files.createDirectories(pluginsPath);
            }
        } catch (IOException e) {
            MessageManager.log("error", "Failed to create plugins directory: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Call a method on all loaded plugins
     */
    public void callMethodOnAllPlugins(String methodName, Object... args) {
        for (OverWatchPlugin pluginInstance : loadedPlugins.values()) {
            try {
                Class<?>[] paramTypes = Arrays.stream(args)
                    .map(Object::getClass)
                    .toArray(Class<?>[]::new);

                Method method = pluginInstance.getClass().getMethod(methodName, paramTypes);
                method.invoke(pluginInstance, args);

            } catch (Exception e) {
                // Method not available or failed - continue with other plugins
            }
        }
    }

    /**
     * Shutdown all plugins
     */
    public void shutdown() {
        // Shutdown all plugins
        for (String pluginName : new HashSet<>(loadedPlugins.keySet())) {
            unloadPlugin(pluginName);
        }

        // Close all class loaders
        for (URLClassLoader classLoader : pluginClassLoaders.values()) {
            try {
                classLoader.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        pluginClassLoaders.clear();
        MessageManager.log("info", "Plugin manager shut down");
    }

    // ===== DATA CLASSES =====

    /**
     * Plugin context provided to plugins
     */
    public static class PluginContext {
        public final OverWatchML mainPlugin;
        public final String pluginName;
        public final PluginManager pluginManager;

        public PluginContext(OverWatchML mainPlugin, String pluginName, PluginManager pluginManager) {
            this.mainPlugin = mainPlugin;
            this.pluginName = pluginName;
            this.pluginManager = pluginManager;
        }
    }

    /**
     * Plugin information
     */
    public static class PluginInfo {
        public final String name;
        public final String version;
        public final String description;
        public final String author;
        public final boolean enabled;

        public PluginInfo(String name, String version, String description, String author, boolean enabled) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.author = author;
            this.enabled = enabled;
        }
    }
}
