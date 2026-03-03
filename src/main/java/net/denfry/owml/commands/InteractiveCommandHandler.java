package net.denfry.owml.commands;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interactive command handler with smart suggestions, help system, and guided workflows.
 * Provides enhanced UX for OverWatchML commands with contextual help and validation.
 *
 * Features:
 * - Smart command suggestions based on context
 * - Interactive help system with clickable options
 * - Command validation with helpful error messages
 * - Guided workflows for complex operations
 * - Command history and favorites
 * - Auto-completion with intelligent ranking
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.8
 */
public class InteractiveCommandHandler {

    private static final OverWatchML plugin = OverWatchML.getInstance();

    // Command metadata
    private final Map<String, CommandInfo> commandRegistry = new ConcurrentHashMap<>();
    private final Map<String, List<String>> commandSuggestions = new ConcurrentHashMap<>();
    private final Map<String, InteractiveWorkflow> activeWorkflows = new ConcurrentHashMap<>();

    // User preferences and history
    private final Map<UUID, UserCommandProfile> userProfiles = new ConcurrentHashMap<>();

    // Interactive sessions
    private final Map<UUID, InteractiveSession> activeSessions = new ConcurrentHashMap<>();

    public InteractiveCommandHandler() {
        initializeCommandRegistry();
        initializeSuggestions();
        startCleanupTask();

        MessageManager.log("info", "Interactive Command Handler initialized");
    }

    /**
     * Initialize command registry with metadata
     */
    private void initializeCommandRegistry() {
        // Main commands
        registerCommand("help", "Show help for commands", Arrays.asList("h", "?"), PermissionLevel.USER,
            Arrays.asList("page"), "Get help on available commands");
        registerCommand("check", "Check player status", Arrays.asList("c", "status"), PermissionLevel.STAFF,
            Arrays.asList("player"), "Check detailed player information");
        registerCommand("alert", "Send alert to staff", Arrays.asList("a", "notify"), PermissionLevel.USER,
            Arrays.asList("message"), "Send notification to online staff");
        registerCommand("reload", "Reload plugin configuration", Arrays.asList("r"), PermissionLevel.ADMIN,
            Collections.emptyList(), "Reload all plugin configurations");
        registerCommand("debug", "Toggle debug mode", Arrays.asList("d"), PermissionLevel.ADMIN,
            Arrays.asList("on", "off"), "Enable/disable debug logging");
        registerCommand("staffgui", "Open staff GUI", Arrays.asList("gui", "menu"), PermissionLevel.STAFF,
            Collections.emptyList(), "Open the staff management interface");
        registerCommand("punish", "Punish a player", Arrays.asList("p", "ban"), PermissionLevel.STAFF,
            Arrays.asList("player", "reason"), "Apply punishment to a player");
        registerCommand("teleport", "Teleport to player", Arrays.asList("tp", "goto"), PermissionLevel.STAFF,
            Arrays.asList("player"), "Teleport to a player's location");

        // ML commands
        registerCommand("ml", "Machine learning commands", Arrays.asList("ai"), PermissionLevel.ADMIN,
            Arrays.asList("train", "status", "analyze", "enable", "disable"), "ML system management");

        // Advanced commands
        registerCommand("stats", "Show plugin statistics", Arrays.asList("s", "info"), PermissionLevel.USER,
            Arrays.asList("detailed"), "Display plugin performance statistics");
        registerCommand("config", "Configuration management", Arrays.asList("cfg"), PermissionLevel.ADMIN,
            Arrays.asList("reload", "save", "reset"), "Manage plugin configuration");
        registerCommand("world", "World-specific settings", Arrays.asList("w"), PermissionLevel.ADMIN,
            Arrays.asList("enable", "disable", "settings"), "Configure world-specific detection");
    }

    /**
     * Register a command with metadata
     */
    private void registerCommand(String name, String description, List<String> aliases,
                               PermissionLevel permission, List<String> parameters, String help) {
        CommandInfo info = new CommandInfo(name, description, aliases, permission, parameters, help);
        commandRegistry.put(name, info);

        // Register aliases
        for (String alias : aliases) {
            commandRegistry.put(alias, info);
        }
    }

    /**
     * Initialize smart suggestions
     */
    private void initializeSuggestions() {
        // Player name suggestions (dynamic)
        commandSuggestions.put("player", Arrays.asList("<player_name>"));

        // Common reasons
        commandSuggestions.put("reason", Arrays.asList(
            "xray_cheating", "speed_hack", "fly_hack", "inventory_hack",
            "griefing", "spam", "inappropriate_behavior"
        ));

        // ML commands
        commandSuggestions.put("ml_train", Arrays.asList("<player>", "cheater", "normal"));
        commandSuggestions.put("ml_analyze", Arrays.asList("<player>", "detailed"));

        // Config commands
        commandSuggestions.put("config_reload", Arrays.asList("all", "detection", "punishment", "ml"));
    }

    /**
     * Handle interactive command execution
     */
    public boolean handleInteractiveCommand(CommandSender sender, String command, String[] args) {
        if (!(sender instanceof Player)) {
            return false; // Interactive features only for players
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        // Check if player has an active workflow
        InteractiveWorkflow workflow = activeWorkflows.get(playerId);
        if (workflow != null && !workflow.isComplete()) {
            return handleWorkflowCommand(player, workflow, command, args);
        }

        // Handle regular commands with enhanced UX
        return handleEnhancedCommand(player, command, args);
    }

    /**
     * Handle enhanced command with better UX
     */
    private boolean handleEnhancedCommand(Player player, String command, String[] args) {
        CommandInfo cmdInfo = commandRegistry.get(command.toLowerCase());
        if (cmdInfo == null) {
            showCommandNotFound(player, command);
            showSimilarCommands(player, command);
            return true;
        }

        // Check permissions
        if (!hasPermission(player, cmdInfo.permission)) {
            showNoPermission(player, cmdInfo);
            return true;
        }

        // Validate arguments
        ValidationResult validation = validateArguments(args, cmdInfo);
        if (!validation.isValid()) {
            showValidationError(player, validation);
            showCommandHelp(player, cmdInfo);
            return true;
        }

        // Show contextual help if no args provided
        if (args.length == 0 && cmdInfo.parameters.size() > 0) {
            showInteractiveHelp(player, cmdInfo);
            return true;
        }

        // Update user profile
        updateUserProfile(player.getUniqueId(), command);

        return false; // Let original handler process the command
    }

    /**
     * Handle workflow-based commands
     */
    private boolean handleWorkflowCommand(Player player, InteractiveWorkflow workflow,
                                        String command, String[] args) {
        if (command.equalsIgnoreCase("cancel")) {
            workflow.cancel();
            activeWorkflows.remove(player.getUniqueId());
            player.sendMessage(Component.text("Workflow cancelled.", NamedTextColor.YELLOW));
            return true;
        }

        if (command.equalsIgnoreCase("help")) {
            workflow.showHelp(player);
            return true;
        }

        return workflow.handleInput(player, args);
    }

    /**
     * Show command not found with suggestions
     */
    private void showCommandNotFound(Player player, String command) {
        Component message = Component.text()
            .append(Component.text("Unknown command: ", NamedTextColor.RED))
            .append(Component.text(command, NamedTextColor.YELLOW))
            .append(Component.text("\n\nDid you mean:", NamedTextColor.GRAY))
            .build();

        player.sendMessage(message);
    }

    /**
     * Show similar commands
     */
    private void showSimilarCommands(Player player, String command) {
        List<String> similar = findSimilarCommands(command);

        if (similar.isEmpty()) {
            player.sendMessage(Component.text("No similar commands found.", NamedTextColor.GRAY));
            return;
        }

        Component message = Component.text("Similar commands:\n", NamedTextColor.GREEN);

        for (String cmd : similar.subList(0, Math.min(5, similar.size()))) {
            CommandInfo info = commandRegistry.get(cmd);
            if (info != null) {
                Component cmdComponent = Component.text()
                    .append(Component.text("  /owml " + cmd, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.suggestCommand("/owml " + cmd)))
                    .append(Component.text(" - " + info.description, NamedTextColor.WHITE))
                    .build();

                message = message.append(cmdComponent).append(Component.text("\n"));
            }
        }

        player.sendMessage(message);
    }

    /**
     * Show interactive help for command
     */
    private void showInteractiveHelp(Player player, CommandInfo cmdInfo) {
        net.kyori.adventure.text.TextComponent.Builder help = Component.text()
            .append(Component.text("Command Help: ", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.text(cmdInfo.name.toUpperCase(), NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text("\n" + cmdInfo.description + "\n\n", NamedTextColor.WHITE))
            .append(Component.text("Usage: ", NamedTextColor.YELLOW))
            .append(Component.text("/owml " + cmdInfo.name, NamedTextColor.AQUA));

        // Add parameters
        for (String param : cmdInfo.parameters) {
            List<String> suggestions = commandSuggestions.get(cmdInfo.name + "_" + param);
            if (suggestions != null && !suggestions.isEmpty()) {
                Component paramComp = Component.text(" [" + param + "=", NamedTextColor.GRAY)
                    .append(Component.text(suggestions.get(0), NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.suggestCommand("/owml " + cmdInfo.name + " " + suggestions.get(0))))
                    .append(Component.text("]", NamedTextColor.GRAY));
                help = help.append(paramComp);
            } else {
                help = help.append(Component.text(" <" + param + ">", NamedTextColor.GRAY));
            }
        }

        help = help.append(Component.text("\n\n" + cmdInfo.helpText, NamedTextColor.WHITE));

        // Add examples if available
        List<String> examples = getCommandExamples(cmdInfo.name);
        if (!examples.isEmpty()) {
            help = help.append(Component.text("\n\nExamples:", NamedTextColor.YELLOW));
            for (String example : examples.subList(0, Math.min(3, examples.size()))) {
                help = help.append(Component.text("\n  ", NamedTextColor.GRAY))
                    .append(Component.text(example, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.suggestCommand(example)));
            }
        }

        player.sendMessage(help);
    }

    /**
     * Show validation error with suggestions
     */
    private void showValidationError(Player player, ValidationResult validation) {
        Component message = Component.text()
            .append(Component.text("РІСњРЉ ", NamedTextColor.RED))
            .append(Component.text(validation.getErrorMessage(), NamedTextColor.WHITE))
            .build();

        player.sendMessage(message);

        // Show suggestions if available
        if (validation.hasSuggestions()) {
            Component suggestions = Component.text("СЂСџвЂ™РЋ Suggestions: ", NamedTextColor.GREEN);
            for (String suggestion : validation.getSuggestions()) {
                suggestions = suggestions.append(
                    Component.text(suggestion, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.suggestCommand(suggestion))
                ).append(Component.text(" ", NamedTextColor.GRAY));
            }
            player.sendMessage(suggestions);
        }
    }

    /**
     * Check if a command is handled by the interactive system
     */
    public boolean isInteractiveCommand(String command) {
        return commandRegistry.containsKey(command.toLowerCase());
    }

    /**
     * Get smart tab completions
     */
    public List<String> getSmartCompletions(Player player, String command, String[] args) {
        List<String> completions = new ArrayList<>();
        CommandInfo cmdInfo = commandRegistry.get(command.toLowerCase());

        if (cmdInfo == null) {
            // Suggest similar commands
            completions.addAll(findSimilarCommands(command));
            return completions.subList(0, Math.min(10, completions.size()));
        }

        int argIndex = args.length - 1;
        String currentArg = args.length > 0 ? args[args.length - 1] : "";

        // Suggest parameters based on position
        if (argIndex < cmdInfo.parameters.size()) {
            String paramName = cmdInfo.parameters.get(argIndex);
            List<String> paramSuggestions = commandSuggestions.get(cmdInfo.name + "_" + paramName);

            if (paramSuggestions != null) {
                for (String suggestion : paramSuggestions) {
                    if (suggestion.toLowerCase().startsWith(currentArg.toLowerCase())) {
                        completions.add(suggestion);
                    }
                }
            }
        }

        // Context-aware suggestions
        addContextAwareSuggestions(player, cmdInfo, args, completions);

        // User history-based suggestions
        addHistoryBasedSuggestions(player.getUniqueId(), cmdInfo, args, completions);

        return completions.subList(0, Math.min(10, completions.size()));
    }

    /**
     * Add context-aware suggestions
     */
    private void addContextAwareSuggestions(Player player, CommandInfo cmdInfo, String[] args, List<String> completions) {
        // Player names for player-related commands
        if (cmdInfo.parameters.contains("player")) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.equals(player)) { // Don't suggest self
                    completions.add(onlinePlayer.getName());
                }
            }
        }

        // Recent offenders for punishment commands
        if (cmdInfo.name.equals("punish")) {
            // This would integrate with detection system to suggest recent offenders
            // For now, just add some examples
            completions.add("recent_cheater");
        }
    }

    /**
     * Add history-based suggestions
     */
    private void addHistoryBasedSuggestions(UUID playerId, CommandInfo cmdInfo, String[] args, List<String> completions) {
        UserCommandProfile profile = userProfiles.get(playerId);
        if (profile != null) {
            List<String> recentArgs = profile.getRecentArguments(cmdInfo.name);
            completions.addAll(recentArgs);
        }
    }

    /**
     * Find similar commands using fuzzy matching
     */
    private List<String> findSimilarCommands(String input) {
        List<CommandSimilarity> similarities = new ArrayList<>();

        for (String cmdName : commandRegistry.keySet()) {
            double similarity = calculateSimilarity(input, cmdName);
            if (similarity > 0.6) { // Only suggest reasonably similar commands
                similarities.add(new CommandSimilarity(cmdName, similarity));
            }
        }

        similarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        List<String> result = new ArrayList<>();
        for (CommandSimilarity sim : similarities.subList(0, Math.min(5, similarities.size()))) {
            result.add(sim.command);
        }

        return result;
    }

    /**
     * Simple string similarity calculation
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;

        // Levenshtein distance based similarity
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }

    /**
     * Levenshtein distance calculation
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * Validate command arguments
     */
    private ValidationResult validateArguments(String[] args, CommandInfo cmdInfo) {
        // Check minimum required arguments
        if (args.length < cmdInfo.parameters.size() && !cmdInfo.parameters.isEmpty()) {
            return ValidationResult.error("Missing required arguments. Expected: " +
                String.join(", ", cmdInfo.parameters));
        }

        // Validate specific argument types
        for (int i = 0; i < Math.min(args.length, cmdInfo.parameters.size()); i++) {
            String param = cmdInfo.parameters.get(i);
            String value = args[i];

            ValidationResult paramValidation = validateParameter(param, value);
            if (!paramValidation.isValid()) {
                return paramValidation;
            }
        }

        return ValidationResult.success();
    }

    /**
     * Validate individual parameter
     */
    private ValidationResult validateParameter(String paramType, String value) {
        switch (paramType.toLowerCase()) {
            case "player":
                if (Bukkit.getPlayer(value) == null && !value.equals("<player_name>")) {
                    List<String> suggestions = new ArrayList<>();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(value.toLowerCase())) {
                            suggestions.add("/owml <command> " + player.getName());
                        }
                    }
                    return ValidationResult.error("Player '" + value + "' is not online", suggestions);
                }
                break;

            case "reason":
                if (value.length() < 3) {
                    return ValidationResult.error("Reason must be at least 3 characters long");
                }
                break;
        }

        return ValidationResult.success();
    }

    /**
     * Check player permissions
     */
    private boolean hasPermission(Player player, PermissionLevel required) {
        switch (required) {
            case USER:
                return true; // All players can use basic commands
            case STAFF:
                return player.hasPermission("OverWatch.staff");
            case ADMIN:
                return player.hasPermission("OverWatch.admin");
            default:
                return false;
        }
    }

    /**
     * Show no permission message
     */
    private void showNoPermission(Player player, CommandInfo cmdInfo) {
        Component message = Component.text()
            .append(Component.text("РІСњРЉ You don't have permission to use ", NamedTextColor.RED))
            .append(Component.text(cmdInfo.name, NamedTextColor.YELLOW))
            .append(Component.text(" command.\n", NamedTextColor.RED))
            .append(Component.text("Required permission: ", NamedTextColor.GRAY))
            .append(Component.text(cmdInfo.permission.toString().toLowerCase(), NamedTextColor.WHITE))
            .build();

        player.sendMessage(message);
    }

    /**
     * Show command help
     */
    private void showCommandHelp(Player player, CommandInfo cmdInfo) {
        showInteractiveHelp(player, cmdInfo);
    }

    /**
     * Update user command profile
     */
    private void updateUserProfile(UUID playerId, String command) {
        UserCommandProfile profile = userProfiles.computeIfAbsent(playerId, k -> new UserCommandProfile());
        profile.recordCommandUsage(command);
    }

    /**
     * Get command examples
     */
    private List<String> getCommandExamples(String command) {
        Map<String, List<String>> examples = new HashMap<>();
        examples.put("check", Arrays.asList("/owml check Steve", "/owml check detailed"));
        examples.put("punish", Arrays.asList("/owml punish Steve xray_cheating", "/owml punish Alex griefing"));
        examples.put("alert", Arrays.asList("/owml alert Suspicious player detected!", "/owml alert Need backup at spawn"));
        examples.put("ml", Arrays.asList("/owml ml status", "/owml ml train Steve cheater"));

        return examples.getOrDefault(command, Collections.emptyList());
    }

    /**
     * Start cleanup task for expired sessions
     */
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Clean up expired workflows and sessions
            long currentTime = System.currentTimeMillis();

            activeWorkflows.entrySet().removeIf(entry -> {
                InteractiveWorkflow workflow = entry.getValue();
                return workflow.isExpired(currentTime);
            });

            activeSessions.entrySet().removeIf(entry -> {
                InteractiveSession session = entry.getValue();
                return session.isExpired(currentTime);
            });

        }, 20 * 60 * 5, 20 * 60 * 5); // Every 5 minutes
    }

    /**
     * Get command statistics
     */
    public Map<String, Object> getCommandStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("registeredCommands", commandRegistry.size());
        stats.put("activeWorkflows", activeWorkflows.size());
        stats.put("activeSessions", activeSessions.size());
        stats.put("userProfiles", userProfiles.size());

        return stats;
    }

    // ===== INNER CLASSES =====

    public enum PermissionLevel {
        USER, STAFF, ADMIN
    }

    public static class CommandInfo {
        public final String name;
        public final String description;
        public final List<String> aliases;
        public final PermissionLevel permission;
        public final List<String> parameters;
        public final String helpText;

        public CommandInfo(String name, String description, List<String> aliases,
                          PermissionLevel permission, List<String> parameters, String helpText) {
            this.name = name;
            this.description = description;
            this.aliases = aliases;
            this.permission = permission;
            this.parameters = parameters;
            this.helpText = helpText;
        }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final List<String> suggestions;

        private ValidationResult(boolean valid, String errorMessage, List<String> suggestions) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null);
        }

        public static ValidationResult error(String message, List<String> suggestions) {
            return new ValidationResult(false, message, suggestions);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public boolean hasSuggestions() { return !suggestions.isEmpty(); }
        public List<String> getSuggestions() { return suggestions; }
    }

    public static class CommandSimilarity {
        public final String command;
        public final double similarity;

        public CommandSimilarity(String command, double similarity) {
            this.command = command;
            this.similarity = similarity;
        }
    }

    public static class UserCommandProfile {
        private final Map<String, Integer> commandUsage = new HashMap<>();
        private final Map<String, List<String>> recentArguments = new HashMap<>();
        private final List<String> commandHistory = new ArrayList<>();
        private static final int MAX_HISTORY = 50;

        public void recordCommandUsage(String command) {
            commandUsage.put(command, commandUsage.getOrDefault(command, 0) + 1);

            commandHistory.add(command);
            if (commandHistory.size() > MAX_HISTORY) {
                commandHistory.remove(0);
            }
        }

        public void recordArguments(String command, String[] args) {
            List<String> argList = new ArrayList<>();
            for (String arg : args) {
                if (!arg.trim().isEmpty()) {
                    argList.add(arg);
                }
            }

            if (!argList.isEmpty()) {
                recentArguments.put(command, argList);
            }
        }

        public List<String> getRecentArguments(String command) {
            return recentArguments.getOrDefault(command, new ArrayList<>());
        }

        public int getCommandUsage(String command) {
            return commandUsage.getOrDefault(command, 0);
        }

        public List<String> getCommandHistory() {
            return new ArrayList<>(commandHistory);
        }
    }

    public abstract static class InteractiveWorkflow {
        protected final UUID playerId;
        protected final long startTime;
        protected final long timeout = 5 * 60 * 1000; // 5 minutes
        protected boolean complete = false;

        public InteractiveWorkflow(UUID playerId) {
            this.playerId = playerId;
            this.startTime = System.currentTimeMillis();
        }

        public abstract boolean handleInput(Player player, String[] args);
        public abstract void showHelp(Player player);
        public abstract void cancel();

        public boolean isComplete() { return complete; }
        public boolean isExpired(long currentTime) { return currentTime - startTime > timeout; }
    }

    public static class InteractiveSession {
        private final UUID playerId;
        private final long startTime;
        private final long timeout = 10 * 60 * 1000; // 10 minutes

        public InteractiveSession(UUID playerId) {
            this.playerId = playerId;
            this.startTime = System.currentTimeMillis();
        }

        public boolean isExpired(long currentTime) {
            return currentTime - startTime > timeout;
        }
    }
}