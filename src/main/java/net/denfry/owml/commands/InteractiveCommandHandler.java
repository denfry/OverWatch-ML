package net.denfry.owml.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;
import net.denfry.owml.utils.Schedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InteractiveCommandHandler {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private final Map<String, InteractiveCommandHandler.CommandInfo> commandRegistry = new ConcurrentHashMap<>();
   private final Map<String, List<String>> commandSuggestions = new ConcurrentHashMap<>();
   private final Map<String, InteractiveCommandHandler.InteractiveWorkflow> activeWorkflows = new ConcurrentHashMap<>();
   private final Map<UUID, InteractiveCommandHandler.UserCommandProfile> userProfiles = new ConcurrentHashMap<>();
   private final Map<UUID, InteractiveCommandHandler.InteractiveSession> activeSessions = new ConcurrentHashMap<>();

   public InteractiveCommandHandler() {
      this.initializeCommandRegistry();
      this.initializeSuggestions();
      this.startCleanupTask();
      MessageManager.log("info", "Interactive Command Handler initialized");
   }

   private void initializeCommandRegistry() {
      this.registerCommand(
         "help",
         "Show help for commands",
         Arrays.asList("h", "?"),
         InteractiveCommandHandler.PermissionLevel.USER,
         Arrays.asList("page"),
         "Get help on available commands"
      );
      this.registerCommand(
         "check",
         "Check player status",
         Arrays.asList("c", "status"),
         InteractiveCommandHandler.PermissionLevel.STAFF,
         Arrays.asList("player"),
         "Check detailed player information"
      );
      this.registerCommand(
         "alert",
         "Send alert to staff",
         Arrays.asList("a", "notify"),
         InteractiveCommandHandler.PermissionLevel.USER,
         Arrays.asList("message"),
         "Send notification to online staff"
      );
      this.registerCommand(
         "reload",
         "Reload plugin configuration",
         Arrays.asList("r"),
         InteractiveCommandHandler.PermissionLevel.ADMIN,
         Collections.emptyList(),
         "Reload all plugin configurations"
      );
      this.registerCommand(
         "debug",
         "Toggle debug mode",
         Arrays.asList("d"),
         InteractiveCommandHandler.PermissionLevel.ADMIN,
         Arrays.asList("on", "off"),
         "Enable/disable debug logging"
      );
      this.registerCommand(
         "staffgui",
         "Open staff GUI",
         Arrays.asList("gui", "menu"),
         InteractiveCommandHandler.PermissionLevel.STAFF,
         Collections.emptyList(),
         "Open the staff management interface"
      );
      this.registerCommand(
         "punish",
         "Punish a player",
         Arrays.asList("p", "ban"),
         InteractiveCommandHandler.PermissionLevel.STAFF,
         Arrays.asList("player", "reason"),
         "Apply punishment to a player"
      );
      this.registerCommand(
         "teleport",
         "Teleport to player",
         Arrays.asList("tp", "goto"),
         InteractiveCommandHandler.PermissionLevel.STAFF,
         Arrays.asList("player"),
         "Teleport to a player's location"
      );
      this.registerCommand(
         "ml",
         "Machine learning commands",
         Arrays.asList("ai"),
         InteractiveCommandHandler.PermissionLevel.ADMIN,
         Arrays.asList("train", "status", "analyze", "enable", "disable"),
         "ML system management"
      );
      this.registerCommand(
         "stats",
         "Show plugin statistics",
         Arrays.asList("s", "info"),
         InteractiveCommandHandler.PermissionLevel.USER,
         Arrays.asList("detailed"),
         "Display plugin performance statistics"
      );
      this.registerCommand(
         "config",
         "Configuration management",
         Arrays.asList("cfg"),
         InteractiveCommandHandler.PermissionLevel.ADMIN,
         Arrays.asList("reload", "save", "reset"),
         "Manage plugin configuration"
      );
      this.registerCommand(
         "world",
         "World-specific settings",
         Arrays.asList("w"),
         InteractiveCommandHandler.PermissionLevel.ADMIN,
         Arrays.asList("enable", "disable", "settings"),
         "Configure world-specific detection"
      );
   }

   private void registerCommand(
      String name, String description, List<String> aliases, InteractiveCommandHandler.PermissionLevel permission, List<String> parameters, String help
   ) {
      InteractiveCommandHandler.CommandInfo info = new InteractiveCommandHandler.CommandInfo(name, description, aliases, permission, parameters, help);
      this.commandRegistry.put(name, info);

      for (String alias : aliases) {
         this.commandRegistry.put(alias, info);
      }
   }

   private void initializeSuggestions() {
      this.commandSuggestions.put("player", Arrays.asList("<player_name>"));
      this.commandSuggestions
         .put("reason", Arrays.asList("xray_cheating", "speed_hack", "fly_hack", "inventory_hack", "griefing", "spam", "inappropriate_behavior"));
      this.commandSuggestions.put("ml_train", Arrays.asList("<player>", "cheater", "normal"));
      this.commandSuggestions.put("ml_analyze", Arrays.asList("<player>", "detailed"));
      this.commandSuggestions.put("config_reload", Arrays.asList("all", "detection", "punishment", "ml"));
   }

   public boolean handleInteractiveCommand(CommandSender sender, String command, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else {
         UUID playerId = player.getUniqueId();
         InteractiveCommandHandler.InteractiveWorkflow workflow = this.activeWorkflows.get(playerId);
         return workflow != null && !workflow.isComplete()
            ? this.handleWorkflowCommand(player, workflow, command, args)
            : this.handleEnhancedCommand(player, command, args);
      }
   }

   private boolean handleEnhancedCommand(Player player, String command, String[] args) {
      InteractiveCommandHandler.CommandInfo cmdInfo = this.commandRegistry.get(command.toLowerCase());
      if (cmdInfo == null) {
         this.showCommandNotFound(player, command);
         this.showSimilarCommands(player, command);
         return true;
      } else if (!this.hasPermission(player, cmdInfo.permission)) {
         this.showNoPermission(player, cmdInfo);
         return true;
      } else {
         InteractiveCommandHandler.ValidationResult validation = this.validateArguments(args, cmdInfo);
         if (!validation.isValid()) {
            this.showValidationError(player, validation);
            this.showCommandHelp(player, cmdInfo);
            return true;
         } else if (args.length == 0 && cmdInfo.parameters.size() > 0) {
            this.showInteractiveHelp(player, cmdInfo);
            return true;
         } else {
            this.updateUserProfile(player.getUniqueId(), command);
            return false;
         }
      }
   }

   private boolean handleWorkflowCommand(Player player, InteractiveCommandHandler.InteractiveWorkflow workflow, String command, String[] args) {
      if (command.equalsIgnoreCase("cancel")) {
         workflow.cancel();
         this.activeWorkflows.remove(player.getUniqueId());
         player.sendMessage(Component.text("Workflow cancelled.", NamedTextColor.YELLOW));
         return true;
      } else if (command.equalsIgnoreCase("help")) {
         workflow.showHelp(player);
         return true;
      } else {
         return workflow.handleInput(player, args);
      }
   }

   private void showCommandNotFound(Player player, String command) {
      Component message = ((Builder)((Builder)((Builder)Component.text().append(Component.text("Unknown command: ", NamedTextColor.RED)))
               .append(Component.text(command, NamedTextColor.YELLOW)))
            .append(Component.text("\n\nDid you mean:", NamedTextColor.GRAY)))
         .build();
      player.sendMessage(message);
   }

   private void showSimilarCommands(Player player, String command) {
      List<String> similar = this.findSimilarCommands(command);
      if (similar.isEmpty()) {
         player.sendMessage(Component.text("No similar commands found.", NamedTextColor.GRAY));
      } else {
         Component message = Component.text("Similar commands:\n", NamedTextColor.GREEN);

         for (String cmd : similar.subList(0, Math.min(5, similar.size()))) {
            InteractiveCommandHandler.CommandInfo info = this.commandRegistry.get(cmd);
            if (info != null) {
               Component cmdComponent = ((Builder)((Builder)Component.text()
                        .append(Component.text("  /owml " + cmd, NamedTextColor.AQUA).clickEvent(ClickEvent.suggestCommand("/owml " + cmd))))
                     .append(Component.text(" - " + info.description, NamedTextColor.WHITE)))
                  .build();
               message = message.append(cmdComponent).append(Component.text("\n"));
            }
         }

         player.sendMessage(message);
      }
   }

   private void showInteractiveHelp(Player player, InteractiveCommandHandler.CommandInfo cmdInfo) {
      Builder help = (Builder)((Builder)((Builder)((Builder)((Builder)Component.text()
                     .append(Component.text("Command Help: ", NamedTextColor.GREEN, new TextDecoration[]{TextDecoration.BOLD})))
                  .append(Component.text(cmdInfo.name.toUpperCase(), NamedTextColor.AQUA, new TextDecoration[]{TextDecoration.BOLD})))
               .append(Component.text("\n" + cmdInfo.description + "\n\n", NamedTextColor.WHITE)))
            .append(Component.text("Usage: ", NamedTextColor.YELLOW)))
         .append(Component.text("/owml " + cmdInfo.name, NamedTextColor.AQUA));

      for (String param : cmdInfo.parameters) {
         List<String> suggestions = this.commandSuggestions.get(cmdInfo.name + "_" + param);
         if (suggestions != null && !suggestions.isEmpty()) {
            Component paramComp = ((TextComponent)Component.text(" [" + param + "=", NamedTextColor.GRAY)
                  .append(
                     Component.text(suggestions.get(0), NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.suggestCommand("/owml " + cmdInfo.name + " " + suggestions.get(0)))
                  ))
               .append(Component.text("]", NamedTextColor.GRAY));
            help = (Builder)help.append(paramComp);
         } else {
            help = (Builder)help.append(Component.text(" <" + param + ">", NamedTextColor.GRAY));
         }
      }

      help = (Builder)help.append(Component.text("\n\n" + cmdInfo.helpText, NamedTextColor.WHITE));
      List<String> examples = this.getCommandExamples(cmdInfo.name);
      if (!examples.isEmpty()) {
         help = (Builder)help.append(Component.text("\n\nExamples:", NamedTextColor.YELLOW));

         for (String example : examples.subList(0, Math.min(3, examples.size()))) {
            help = (Builder)((Builder)help.append(Component.text("\n  ", NamedTextColor.GRAY)))
               .append(Component.text(example, NamedTextColor.AQUA).clickEvent(ClickEvent.suggestCommand(example)));
         }
      }

      player.sendMessage(help);
   }

   private void showValidationError(Player player, InteractiveCommandHandler.ValidationResult validation) {
      Component message = ((Builder)((Builder)Component.text().append(Component.text("❌ ", NamedTextColor.RED)))
            .append(Component.text(validation.getErrorMessage(), NamedTextColor.WHITE)))
         .build();
      player.sendMessage(message);
      if (validation.hasSuggestions()) {
         Component suggestions = Component.text("\ud83d\udca1 Suggestions: ", NamedTextColor.GREEN);

         for (String suggestion : validation.getSuggestions()) {
            suggestions = suggestions.append(Component.text(suggestion, NamedTextColor.AQUA).clickEvent(ClickEvent.suggestCommand(suggestion)))
               .append(Component.text(" ", NamedTextColor.GRAY));
         }

         player.sendMessage(suggestions);
      }
   }

   public boolean isInteractiveCommand(String command) {
      return this.commandRegistry.containsKey(command.toLowerCase());
   }

   public List<String> getSmartCompletions(Player player, String command, String[] args) {
      List<String> completions = new ArrayList<>();
      InteractiveCommandHandler.CommandInfo cmdInfo = this.commandRegistry.get(command.toLowerCase());
      if (cmdInfo == null) {
         completions.addAll(this.findSimilarCommands(command));
         return completions.subList(0, Math.min(10, completions.size()));
      } else {
         int argIndex = args.length - 1;
         String currentArg = args.length > 0 ? args[args.length - 1] : "";
         if (argIndex < cmdInfo.parameters.size()) {
            String paramName = cmdInfo.parameters.get(argIndex);
            List<String> paramSuggestions = this.commandSuggestions.get(cmdInfo.name + "_" + paramName);
            if (paramSuggestions != null) {
               for (String suggestion : paramSuggestions) {
                  if (suggestion.toLowerCase().startsWith(currentArg.toLowerCase())) {
                     completions.add(suggestion);
                  }
               }
            }
         }

         this.addContextAwareSuggestions(player, cmdInfo, args, completions);
         this.addHistoryBasedSuggestions(player.getUniqueId(), cmdInfo, args, completions);
         return completions.subList(0, Math.min(10, completions.size()));
      }
   }

   private void addContextAwareSuggestions(Player player, InteractiveCommandHandler.CommandInfo cmdInfo, String[] args, List<String> completions) {
      if (cmdInfo.parameters.contains("player")) {
         for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
               completions.add(onlinePlayer.getName());
            }
         }
      }

      if (cmdInfo.name.equals("punish")) {
         completions.add("recent_cheater");
      }
   }

   private void addHistoryBasedSuggestions(UUID playerId, InteractiveCommandHandler.CommandInfo cmdInfo, String[] args, List<String> completions) {
      InteractiveCommandHandler.UserCommandProfile profile = this.userProfiles.get(playerId);
      if (profile != null) {
         List<String> recentArgs = profile.getRecentArguments(cmdInfo.name);
         completions.addAll(recentArgs);
      }
   }

   private List<String> findSimilarCommands(String input) {
      List<InteractiveCommandHandler.CommandSimilarity> similarities = new ArrayList<>();

      for (String cmdName : this.commandRegistry.keySet()) {
         double similarity = this.calculateSimilarity(input, cmdName);
         if (similarity > 0.6) {
            similarities.add(new InteractiveCommandHandler.CommandSimilarity(cmdName, similarity));
         }
      }

      similarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));
      List<String> result = new ArrayList<>();

      for (InteractiveCommandHandler.CommandSimilarity sim : similarities.subList(0, Math.min(5, similarities.size()))) {
         result.add(sim.command);
      }

      return result;
   }

   private double calculateSimilarity(String s1, String s2) {
      if (s1.equals(s2)) {
         return 1.0;
      } else {
         int maxLen = Math.max(s1.length(), s2.length());
         if (maxLen == 0) {
            return 1.0;
         } else {
            int distance = this.levenshteinDistance(s1, s2);
            return 1.0 - (double)distance / maxLen;
         }
      }
   }

   private int levenshteinDistance(String s1, String s2) {
      int len1 = s1.length();
      int len2 = s2.length();
      int[][] dp = new int[len1 + 1][len2 + 1];
      int i = 0;

      while (i <= len1) {
         dp[i][0] = i++;
      }

      i = 0;

      while (i <= len2) {
         dp[0][i] = i++;
      }

      for (int ix = 1; ix <= len1; ix++) {
         for (int j = 1; j <= len2; j++) {
            int cost = s1.charAt(ix - 1) == s2.charAt(j - 1) ? 0 : 1;
            dp[ix][j] = Math.min(Math.min(dp[ix - 1][j] + 1, dp[ix][j - 1] + 1), dp[ix - 1][j - 1] + cost);
         }
      }

      return dp[len1][len2];
   }

   private InteractiveCommandHandler.ValidationResult validateArguments(String[] args, InteractiveCommandHandler.CommandInfo cmdInfo) {
      if (args.length < cmdInfo.parameters.size() && !cmdInfo.parameters.isEmpty()) {
         return InteractiveCommandHandler.ValidationResult.error("Missing required arguments. Expected: " + String.join(", ", cmdInfo.parameters));
      } else {
         for (int i = 0; i < Math.min(args.length, cmdInfo.parameters.size()); i++) {
            String param = cmdInfo.parameters.get(i);
            String value = args[i];
            InteractiveCommandHandler.ValidationResult paramValidation = this.validateParameter(param, value);
            if (!paramValidation.isValid()) {
               return paramValidation;
            }
         }

         return InteractiveCommandHandler.ValidationResult.success();
      }
   }

   private InteractiveCommandHandler.ValidationResult validateParameter(String paramType, String value) {
      String var3 = paramType.toLowerCase();
      switch (var3) {
         case "player":
            if (Bukkit.getPlayer(value) == null && !value.equals("<player_name>")) {
               List<String> suggestions = new ArrayList<>();

               for (Player player : Bukkit.getOnlinePlayers()) {
                  if (player.getName().toLowerCase().startsWith(value.toLowerCase())) {
                     suggestions.add("/owml <command> " + player.getName());
                  }
               }

               return InteractiveCommandHandler.ValidationResult.error("Player '" + value + "' is not online", suggestions);
            }
            break;
         case "reason":
            if (value.length() < 3) {
               return InteractiveCommandHandler.ValidationResult.error("Reason must be at least 3 characters long");
            }
      }

      return InteractiveCommandHandler.ValidationResult.success();
   }

   private boolean hasPermission(Player player, InteractiveCommandHandler.PermissionLevel required) {
      switch (required) {
         case USER:
            return true;
         case STAFF:
            return player.hasPermission("OverWatch.staff");
         case ADMIN:
            return player.hasPermission("OverWatch.admin");
         default:
            return false;
      }
   }

   private void showNoPermission(Player player, InteractiveCommandHandler.CommandInfo cmdInfo) {
      Component message = ((Builder)((Builder)((Builder)((Builder)((Builder)Component.text()
                        .append(Component.text("❌ You don't have permission to use ", NamedTextColor.RED)))
                     .append(Component.text(cmdInfo.name, NamedTextColor.YELLOW)))
                  .append(Component.text(" command.\n", NamedTextColor.RED)))
               .append(Component.text("Required permission: ", NamedTextColor.GRAY)))
            .append(Component.text(cmdInfo.permission.toString().toLowerCase(), NamedTextColor.WHITE)))
         .build();
      player.sendMessage(message);
   }

   private void showCommandHelp(Player player, InteractiveCommandHandler.CommandInfo cmdInfo) {
      this.showInteractiveHelp(player, cmdInfo);
   }

   private void updateUserProfile(UUID playerId, String command) {
      InteractiveCommandHandler.UserCommandProfile profile = this.userProfiles
         .computeIfAbsent(playerId, k -> new InteractiveCommandHandler.UserCommandProfile());
      profile.recordCommandUsage(command);
   }

   private List<String> getCommandExamples(String command) {
      Map<String, List<String>> examples = new HashMap<>();
      examples.put("check", Arrays.asList("/owml check Steve", "/owml check detailed"));
      examples.put("punish", Arrays.asList("/owml punish Steve xray_cheating", "/owml punish Alex griefing"));
      examples.put("alert", Arrays.asList("/owml alert Suspicious player detected!", "/owml alert Need backup at spawn"));
      examples.put("ml", Arrays.asList("/owml ml status", "/owml ml train Steve cheater"));
      return examples.getOrDefault(command, Collections.emptyList());
   }

   private void startCleanupTask() {
      Schedulers.globalTimer(() -> {
         long currentTime = System.currentTimeMillis();
         this.activeWorkflows.entrySet().removeIf(entry -> {
            InteractiveCommandHandler.InteractiveWorkflow workflow = entry.getValue();
            return workflow.isExpired(currentTime);
         });
         this.activeSessions.entrySet().removeIf(entry -> {
            InteractiveCommandHandler.InteractiveSession session = entry.getValue();
            return session.isExpired(currentTime);
         });
      }, 6000L, 6000L);
   }

   public Map<String, Object> getCommandStats() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("registeredCommands", this.commandRegistry.size());
      stats.put("activeWorkflows", this.activeWorkflows.size());
      stats.put("activeSessions", this.activeSessions.size());
      stats.put("userProfiles", this.userProfiles.size());
      return stats;
   }

   public static class CommandInfo {
      public final String name;
      public final String description;
      public final List<String> aliases;
      public final InteractiveCommandHandler.PermissionLevel permission;
      public final List<String> parameters;
      public final String helpText;

      public CommandInfo(
         String name, String description, List<String> aliases, InteractiveCommandHandler.PermissionLevel permission, List<String> parameters, String helpText
      ) {
         this.name = name;
         this.description = description;
         this.aliases = aliases;
         this.permission = permission;
         this.parameters = parameters;
         this.helpText = helpText;
      }
   }

   public static class CommandSimilarity {
      public final String command;
      public final double similarity;

      public CommandSimilarity(String command, double similarity) {
         this.command = command;
         this.similarity = similarity;
      }
   }

   public static class InteractiveSession {
      private final UUID playerId;
      private final long startTime;
      private final long timeout = 600000L;

      public InteractiveSession(UUID playerId) {
         this.playerId = playerId;
         this.startTime = System.currentTimeMillis();
      }

      public boolean isExpired(long currentTime) {
         return currentTime - this.startTime > 600000L;
      }
   }

   public abstract static class InteractiveWorkflow {
      protected final UUID playerId;
      protected final long startTime;
      protected final long timeout = 300000L;
      protected boolean complete = false;

      public InteractiveWorkflow(UUID playerId) {
         this.playerId = playerId;
         this.startTime = System.currentTimeMillis();
      }

      public abstract boolean handleInput(Player var1, String[] var2);

      public abstract void showHelp(Player var1);

      public abstract void cancel();

      public boolean isComplete() {
         return this.complete;
      }

      public boolean isExpired(long currentTime) {
         return currentTime - this.startTime > 300000L;
      }
   }

   public static enum PermissionLevel {
      USER,
      STAFF,
      ADMIN;
   }

   public static class UserCommandProfile {
      private final Map<String, Integer> commandUsage = new ConcurrentHashMap<>();
      private final Map<String, List<String>> recentArguments = new ConcurrentHashMap<>();
      private final List<String> commandHistory = new ArrayList<>();
      private static final int MAX_HISTORY = 50;

      public void recordCommandUsage(String command) {
         this.commandUsage.put(command, this.commandUsage.getOrDefault(command, 0) + 1);
         this.commandHistory.add(command);
         if (this.commandHistory.size() > 50) {
            this.commandHistory.remove(0);
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
            this.recentArguments.put(command, argList);
         }
      }

      public List<String> getRecentArguments(String command) {
         return this.recentArguments.getOrDefault(command, new ArrayList<>());
      }

      public int getCommandUsage(String command) {
         return this.commandUsage.getOrDefault(command, 0);
      }

      public List<String> getCommandHistory() {
         return new ArrayList<>(this.commandHistory);
      }
   }

   public static class ValidationResult {
      private final boolean valid;
      private final String errorMessage;
      private final List<String> suggestions;

      private ValidationResult(boolean valid, String errorMessage, List<String> suggestions) {
         this.valid = valid;
         this.errorMessage = errorMessage;
         this.suggestions = (List<String>)(suggestions != null ? suggestions : new ArrayList<>());
      }

      public static InteractiveCommandHandler.ValidationResult success() {
         return new InteractiveCommandHandler.ValidationResult(true, null, null);
      }

      public static InteractiveCommandHandler.ValidationResult error(String message) {
         return new InteractiveCommandHandler.ValidationResult(false, message, null);
      }

      public static InteractiveCommandHandler.ValidationResult error(String message, List<String> suggestions) {
         return new InteractiveCommandHandler.ValidationResult(false, message, suggestions);
      }

      public boolean isValid() {
         return this.valid;
      }

      public String getErrorMessage() {
         return this.errorMessage;
      }

      public boolean hasSuggestions() {
         return !this.suggestions.isEmpty();
      }

      public List<String> getSuggestions() {
         return this.suggestions;
      }
   }
}
