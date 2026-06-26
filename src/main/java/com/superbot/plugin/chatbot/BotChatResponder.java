package com.superbot.plugin.chatbot;

import com.superbot.plugin.SuperBotPlugin;
import com.superbot.plugin.data.BotInstance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotChatResponder implements Listener {

    private final SuperBotPlugin plugin;
    // Pattern: @BotName (case-insensitive, strips & color codes from name)
    private final Pattern mentionPattern;

    public BotChatResponder(SuperBotPlugin plugin) {
        this.plugin = plugin;
        // Strip color codes from bot name to get plain name for @mention
        String rawName = plugin.getConfig().getString("bot-name", "&6SuperBot")
                .replaceAll("&[0-9a-fA-FklmnorKLMNOR]", "");
        mentionPattern = Pattern.compile("@" + Pattern.quote(rawName), Pattern.CASE_INSENSITIVE);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("chatbot.enabled", true)) return;

        String message = event.getMessage();
        Player sender  = event.getPlayer();

        // Check if message mentions @SuperBot (or whatever the bot is named)
        if (!mentionPattern.matcher(message).find()) return;

        // Find the bot owned by anyone (any bot can respond)
        BotInstance bot = plugin.getDataManager().getAllBots().stream().findFirst().orElse(null);
        if (bot == null) return;
        if (bot.getNpc() == null || !bot.getNpc().isSpawned()) return;

        // Strip the @mention from the question
        String question = mentionPattern.matcher(message).replaceAll("").trim();
        if (question.isEmpty()) {
            question = "How can I help you with Minecraft?";
        }

        String botDisplayName = bot.getNpc().getName();
        String finalQuestion  = question;

        // Call AI API on async thread (we're already async)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String response = askAI(finalQuestion, sender.getName());
            if (response == null) return;

            // Send response on main thread as bot chat
            String botName = botDisplayName;
            String reply   = response;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Broadcast as if the bot is speaking
                Bukkit.broadcastMessage("§6[SuperBot] §f" + reply);
            });
        });
    }

    private String askAI(String question, String playerName) {
        String apiKey = plugin.getConfig().getString("chatbot.api-key", "");
        if (apiKey.isEmpty()) {
            plugin.getLogger().warning("Chatbot API key not set in config.yml!");
            return null;
        }

        String model      = plugin.getConfig().getString("chatbot.model", "claude-haiku-4-5-20251001");
        int    maxTokens  = plugin.getConfig().getInt("chatbot.max-tokens", 200);
        String systemPrompt = plugin.getConfig().getString("chatbot.system-prompt",
                "You are SuperBot, a helpful Minecraft assistant. Answer questions about Minecraft concisely in 1-3 sentences. Only answer Minecraft-related questions.");

        try {
            URL url = new URL("https://api.anthropic.com/v1/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            String body = String.format("""
                {
                  "model": "%s",
                  "max_tokens": %d,
                  "system": "%s",
                  "messages": [
                    {"role": "user", "content": "%s"}
                  ]
                }
                """,
                    model,
                    maxTokens,
                    systemPrompt.replace("\"", "\\\""),
                    question.replace("\"", "\\\"").replace("\n", " ")
            );

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            String json = sb.toString();

            // Parse "text" field from response (simple string extraction, no external lib needed)
            Pattern textPattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
            Matcher m = textPattern.matcher(json);
            if (m.find()) {
                return m.group(1)
                        .replace("\\n", " ")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
            }

            plugin.getLogger().warning("Unexpected API response: " + json.substring(0, Math.min(200, json.length())));
            return null;

        } catch (Exception e) {
            plugin.getLogger().warning("Chatbot API error: " + e.getMessage());
            return null;
        }
    }
}
