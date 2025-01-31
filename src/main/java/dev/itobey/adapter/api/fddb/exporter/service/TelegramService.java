package dev.itobey.adapter.api.fddb.exporter.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to send messages to Telegram.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private final FddbExporterProperties properties;
    private final TelegramBot telegramBot;

    /**
     * Sends a message to the configured Telegram chat.
     *
     * @param message The message to send.
     */
    public void sendMessage(String message) {
        FddbExporterProperties.Notification.Telegram telegram = properties.getNotification().getTelegram();
        SendMessage request = new SendMessage(telegram.getChatId(), message).parseMode(ParseMode.Markdown);
        SendResponse response = telegramBot.execute(request);

        if (response.isOk()) {
            log.debug("Telegram message sent successfully");
        } else {
            log.error("Failed to send Telegram message: {}", response.description());
        }
    }

}