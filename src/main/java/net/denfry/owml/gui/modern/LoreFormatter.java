package net.denfry.owml.gui.modern;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Форматирует лор предметов, ограничивая длину строк.
 */
public class LoreFormatter {
    private static final int MAX_LINE_LENGTH = 40;
    private static final String DEFAULT_COLOR = ChatColor.GRAY.toString();

    public static List<String> format(List<String> rawLines) {
        List<String> formattedLines = new ArrayList<>();
        for (String line : rawLines) {
            if (line == null || line.isEmpty()) {
                formattedLines.add("");
                continue;
            }

            String currentLine = line.startsWith("§") ? line : DEFAULT_COLOR + line;
            if (ChatColor.stripColor(currentLine).length() <= MAX_LINE_LENGTH) {
                formattedLines.add(currentLine);
            } else {
                formattedLines.addAll(wrap(currentLine));
            }
        }
        return formattedLines;
    }

    private static List<String> wrap(String text) {
        List<String> result = new ArrayList<>();
        String lastColors = ChatColor.getLastColors(text);
        if (lastColors.isEmpty()) lastColors = DEFAULT_COLOR;

        String plainText = ChatColor.stripColor(text);
        int start = 0;
        while (start < plainText.length()) {
            int end = Math.min(start + MAX_LINE_LENGTH, plainText.length());
            
            // Пытаемся перенести по пробелу
            if (end < plainText.length()) {
                int lastSpace = plainText.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            String sub = plainText.substring(start, end).trim();
            if (!sub.isEmpty()) {
                result.add(lastColors + sub);
            }
            start = end + 1;
        }
        return result;
    }
}
