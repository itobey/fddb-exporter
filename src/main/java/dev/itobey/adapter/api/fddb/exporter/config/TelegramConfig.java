package dev.itobey.adapter.api.fddb.exporter.config;

import com.pengrad.telegrambot.TelegramBot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class TelegramConfig {

    private final FddbExporterProperties properties;

    @Bean
    public TelegramBot telegramBot() {
        return new TelegramBot(properties.getNotification().getTelegram().getToken());
    }
}
