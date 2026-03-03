package net.denfry.owml.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.denfry.owml.OverWatchML;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Centralized message management system for OverWatch-ML.
 * Provides localized, formatted, and consistent messaging throughout the plugin.
 * Supports multiple languages via .properties files.
 *
 * @author OverWatch Team
 * @version 2.0.0
 * @since 1.8.1
 */
public class MessageManager {

    private static final OverWatchML plugin = OverWatchML.getInstance();
    private static final Map<String, String> messages = new ConcurrentHashMap<>();
    private static final Map<String, Properties> languageFiles = new ConcurrentHashMap<>();
    private static String currentLanguage = "en";

    // Color scheme
    public static final TextColor PRIMARY = NamedTextColor.GREEN;
    public static final TextColor SECONDARY = NamedTextColor.GOLD;
    public static final TextColor ACCENT = NamedTextColor.AQUA;
    public static final TextColor ERROR = NamedTextColor.RED;
    public static final TextColor WARNING = NamedTextColor.YELLOW;
    public static final TextColor SUCCESS = NamedTextColor.GREEN;
    public static final TextColor INFO = NamedTextColor.GRAY;

    // Color code pattern
    private static final Pattern COLOR_PATTERN = Pattern.compile("([&В§])([0-9a-fk-or])");

    static {
        loadDefaultMessages();
    }

    /**
     * Load default English messages from embedded properties file.
     */
    private static void loadDefaultMessages() {
        try (InputStream stream = MessageManager.class.getClassLoader().getResourceAsStream("messages.properties")) {
            if (stream != null) {
                Properties props = new Properties();
                props.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                languageFiles.put("en", props);

                // Load messages into cache
                for (String key : props.stringPropertyNames()) {
                    messages.put(key, props.getProperty(key));
                }
            } else {
                plugin.getLogger().warning("Could not load default messages.properties file!");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load default messages: " + e.getMessage());
        }
    }

    /**
     * Load language file from plugin data folder.
     */
    public static void loadLanguageFile(String languageCode) {
        File langFile = new File(plugin.getDataFolder(), "messages_" + languageCode + ".properties");
        if (!langFile.exists()) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(langFile);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {

            Properties props = new Properties();
            props.load(reader);
            languageFiles.put(languageCode, props);

            // Reload messages if this is the current language
            if (languageCode.equals(currentLanguage)) {
                reloadMessages();
            }

        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load language file " + langFile.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Set the current language.
     */
    public static void setLanguage(String languageCode) {
        if (languageFiles.containsKey(languageCode)) {
            currentLanguage = languageCode;
            reloadMessages();
            plugin.getLogger().info("Language changed to: " + languageCode);
        } else {
            plugin.getLogger().warning("Language file not found: " + languageCode);
        }
    }

    /**
     * Reload messages for current language.
     */
    private static void reloadMessages() {
        Properties props = languageFiles.get(currentLanguage);
        if (props != null) {
            messages.clear();
            for (String key : props.stringPropertyNames()) {
                messages.put(key, props.getProperty(key));
            }
        }
    }

    /**
     * Get available languages.
     */
    public static String[] getAvailableLanguages() {
        return languageFiles.keySet().toArray(new String[0]);
    }

    /**
     * Get current language.
     */
    public static String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Sends a message to a command sender using a message key.
     *
     * @param sender the recipient
     * @param key the message key
     * @param placeholders placeholder replacements
     */
    public static void send(@NotNull CommandSender sender, @NotNull String key, Object... placeholders) {
        String message = messages.get(key);
        if (message == null) {
            // Fallback for missing messages
            sender.sendMessage(Component.text("Missing message: " + key).color(ERROR));
            return;
        }

        Component component = parseMessage(message, placeholders);
        sender.sendMessage(component);
    }

    /**
     * Gets a formatted component for a message key.
     *
     * @param key the message key
     * @param placeholders placeholder replacements
     * @return the formatted component
     */
    @NotNull
    public static Component get(@NotNull String key, Object... placeholders) {
        String message = messages.get(key);
        if (message == null) {
            return Component.text("Missing message: " + key).color(ERROR);
        }
        return parseMessage(message, placeholders);
    }

    /**
     * Sends a success message.
     */
    public static void sendSuccess(@NotNull CommandSender sender, @NotNull String message, Object... placeholders) {
        Component component = Component.text("вњ… ").color(SUCCESS)
                .append(Component.text(message, SUCCESS));
        sender.sendMessage(replacePlaceholders(component, placeholders));
    }

    /**
     * Sends an error message.
     */
    public static void sendError(@NotNull CommandSender sender, @NotNull String message, Object... placeholders) {
        Component component = Component.text("вќЊ ").color(ERROR)
                .append(Component.text(message, ERROR));
        sender.sendMessage(replacePlaceholders(component, placeholders));
    }

    /**
     * Sends an info message.
     */
    public static void sendInfo(@NotNull CommandSender sender, @NotNull String message, Object... placeholders) {
        Component component = Component.text("в„№ ").color(INFO)
                .append(Component.text(message, INFO));
        sender.sendMessage(replacePlaceholders(component, placeholders));
    }

    /**
     * Sends a warning message.
     */
    public static void sendWarning(@NotNull CommandSender sender, @NotNull String message, Object... placeholders) {
        Component component = Component.text("вљ  ").color(WARNING)
                .append(Component.text(message, WARNING));
        sender.sendMessage(replacePlaceholders(component, placeholders));
    }

    /**
     * Broadcasts a message to all online players with a specific permission.
     */
    public static void broadcastToPermission(@NotNull String permission, @NotNull String key, Object... placeholders) {
        Component message = get(key, placeholders);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Logs a message to console.
     */
    public static void log(@NotNull String level, @NotNull String message, Object... placeholders) {
        String formatted = replaceStringPlaceholders(message, placeholders);
        switch (level.toLowerCase()) {
            case "info" -> plugin.getLogger().info(formatted);
            case "warning" -> plugin.getLogger().warning(formatted);
            case "error" -> plugin.getLogger().severe(formatted);
            default -> plugin.getLogger().info(formatted);
        }

        // Also log to web panel
        try {
            net.denfry.owml.web.WebLogHandler.addLog(level.toUpperCase(), formatted);
        } catch (Exception e) {
            // Ignore if web logging fails
        }
    }

    /**
     * Logs the plugin branding/ASCII art to console.
     */
    public static void logBrand() {
        String[] logo = {
            "§b   ____                 _戴_  _      _          __  __ _     ",
            "§b  / __ \\               | |  | |    | |        |  \\/  | |    ",
            "§b | |  | |_   _____ _ __| |  | |__ _| |_ ___| |  \\/  | |    ",
            "§b | |  | \\ \\ / / _ \\ '__| |/\\| / _` | __/ __| | |\\/| | |    ",
            "§b | |__| |\\ V /  __/ |  \\  /\\  / (_| | || (__| | |  | | |____",
            "§b  \\____/  \\_/ \\___|_|   \\/  \\/ \\__,_|\\__\\___|_| |_ |_|______|",
            "§3                                                             ",
            "§a             Advanced ML-Powered X-Ray Detection             ",
            "§7                    Version: §f" + plugin.getDescription().getVersion(),
            "§7                    Author: §f" + String.join(", ", plugin.getDescription().getAuthors()),
            "§7"
        };

        for (String line : logo) {
            plugin.getServer().getConsoleSender().sendMessage(TextUtils.colorize(line));
        }
    }

    /**
     * Logs the system information to console.
     */
    public static void logSystemInfo() {
        long maxMem = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long totalMem = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMem = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        int cores = Runtime.getRuntime().availableProcessors();
        String os = System.getProperty("os.name");
        String javaVer = System.getProperty("java.version");

        logConsole("info", "SYSTEM", "§7OS: §f" + os + " §7| Java: §f" + javaVer + " §7| Cores: §f" + cores);
        logConsole("info", "SYSTEM", "§7Memory: §f" + (totalMem - freeMem) + "MB §7/ §f" + totalMem + "MB §7(Max: §f" + maxMem + "MB)");
    }

    /**
     * Logs a detailed message to console with timestamp and plugin prefix.
     */
    public static void logConsole(@NotNull String level, @NotNull String category, @NotNull String message, Object... placeholders) {
        String formatted = replaceStringPlaceholders(message, placeholders);
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String prefixedMessage = String.format("[%s] [%s] [%s] %s", timestamp, "OverWatch-ML", category.toUpperCase(), formatted);

        switch (level.toLowerCase()) {
            case "info" -> plugin.getLogger().info(prefixedMessage);
            case "warning" -> plugin.getLogger().warning(prefixedMessage);
            case "error" -> plugin.getLogger().severe(prefixedMessage);
            case "debug" -> {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("[DEBUG] " + prefixedMessage);
                }
            }
            default -> plugin.getLogger().info(prefixedMessage);
        }
    }

    /**
     * Logs plugin initialization events to console.
     */
    public static void logInit(@NotNull String component, @NotNull String status, Object... placeholders) {
        String message = component + " - " + status;
        if (placeholders.length > 0) {
            message = replaceStringPlaceholders(message, placeholders);
        }
        logConsole("info", "INIT", message);
    }

    /**
     * Logs plugin startup events to console.
     */
    public static void logStartup(@NotNull String event, Object... placeholders) {
        String message = replaceStringPlaceholders(event, placeholders);
        logConsole("info", "STARTUP", message);
    }

    /**
     * Logs performance metrics to console.
     */
    public static void logPerformance(@NotNull String metric, @NotNull String value, Object... placeholders) {
        String message = metric + ": " + value;
        if (placeholders.length > 0) {
            message = replaceStringPlaceholders(message, placeholders);
        }
        logConsole("info", "PERF", message);
    }

    /**
     * Logs dashboard/panel events to console.
     */
    public static void logDashboard(@NotNull String action, @NotNull String details, Object... placeholders) {
        String message = action + " - " + details;
        if (placeholders.length > 0) {
            message = replaceStringPlaceholders(message, placeholders);
        }
        logConsole("info", "DASHBOARD", message);
    }

    /**
     * Logs security-related events to console.
     */
    public static void logSecurity(@NotNull String event, @NotNull String details, Object... placeholders) {
        String message = event + ": " + details;
        if (placeholders.length > 0) {
            message = replaceStringPlaceholders(message, placeholders);
        }
        logConsole("warning", "SECURITY", message);
    }

    /**
     * Logs detection events to console.
     */
    public static void logDetection(@NotNull String type, @NotNull String player, @NotNull String details, Object... placeholders) {
        String message = String.format("%s | Player: %s | %s", type, player, details);
        if (placeholders.length > 0) {
            message = replaceStringPlaceholders(message, placeholders);
        }
        logConsole("warning", "DETECTION", message);
    }

    /**
     * Parse message with placeholders and color codes.
     */
    private static Component parseMessage(String message, Object[] placeholders) {
        // Replace placeholders first
        message = replaceStringPlaceholders(message, placeholders);

        // Parse color codes (&)
        return parseColors(message);
    }

    /**
     * Parse color codes in message.
     */
    private static Component parseColors(String message) {
        Component component = Component.empty();
        Matcher matcher = COLOR_PATTERN.matcher(message);

        int lastEnd = 0;
        TextColor currentColor = INFO; // Default color

        while (matcher.find()) {
            // Add text before color code
            if (matcher.start() > lastEnd) {
                String text = message.substring(lastEnd, matcher.start());
                component = component.append(Component.text(text).color(currentColor));
            }

            // Parse color code
            String colorChar = matcher.group(2).toLowerCase();
            currentColor = getColorFromCode(colorChar);

            lastEnd = matcher.end();
        }

        // Add remaining text
        if (lastEnd < message.length()) {
            String remaining = message.substring(lastEnd);
            component = component.append(Component.text(remaining).color(currentColor));
        }

        return component;
    }

    /**
     * Convert color code to TextColor.
     */
    private static TextColor getColorFromCode(String code) {
        return switch (code) {
            case "0" -> NamedTextColor.BLACK;
            case "1" -> NamedTextColor.DARK_BLUE;
            case "2" -> NamedTextColor.DARK_GREEN;
            case "3" -> NamedTextColor.DARK_AQUA;
            case "4" -> NamedTextColor.DARK_RED;
            case "5" -> NamedTextColor.DARK_PURPLE;
            case "6" -> NamedTextColor.GOLD;
            case "7" -> NamedTextColor.GRAY;
            case "8" -> NamedTextColor.DARK_GRAY;
            case "9" -> NamedTextColor.BLUE;
            case "a" -> NamedTextColor.GREEN;
            case "b" -> NamedTextColor.AQUA;
            case "c" -> NamedTextColor.RED;
            case "d" -> NamedTextColor.LIGHT_PURPLE;
            case "e" -> NamedTextColor.YELLOW;
            case "f" -> NamedTextColor.WHITE;
            default -> INFO;
        };
    }

    /**
     * Replaces placeholders in a component.
     */
    private static Component replacePlaceholders(Component component, Object[] placeholders) {
        if (placeholders.length == 0) return component;

        String text = component.toString(); // Simplified placeholder replacement
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String placeholder = "{" + placeholders[i] + "}";
                String value = placeholders[i + 1].toString();
                text = text.replace(placeholder, value);
            }
        }

        return Component.text(text).color(component.color()).decorations(component.decorations());
    }

    /**
     * Replaces placeholders in a string.
     */
    private static String replaceStringPlaceholders(String text, Object[] placeholders) {
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String placeholder = "{" + placeholders[i] + "}";
                Object valueObj = placeholders[i + 1];
                String value = valueObj != null ? valueObj.toString() : "null";
                text = text.replace(placeholder, value);
            }
        }
        return text;
    }
}
