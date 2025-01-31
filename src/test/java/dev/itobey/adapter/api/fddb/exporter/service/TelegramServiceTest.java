package dev.itobey.adapter.api.fddb.exporter.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramServiceTest {

    @InjectMocks
    private TelegramService telegramService;

    @Mock
    private TelegramBot telegramBot;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private FddbExporterProperties properties;

    @Test
    void shouldSendMessageSuccessfully() {
        // given
        SendResponse sendResponse = mock(SendResponse.class);
        when(properties.getNotification().getTelegram().getChatId()).thenReturn("test-chat-id");
        when(telegramBot.execute(any(SendMessage.class))).thenReturn(sendResponse);
        when(sendResponse.isOk()).thenReturn(true);

        // when
        telegramService.sendMessage("Test message");

        // then
        verify(telegramBot).execute(any(SendMessage.class));
        verify(sendResponse).isOk();
    }

    @Test
    void shouldHandleFailedMessage() {
        // given
        SendResponse sendResponse = mock(SendResponse.class);
        when(properties.getNotification().getTelegram().getChatId()).thenReturn("test-chat-id");
        when(telegramBot.execute(any(SendMessage.class))).thenReturn(sendResponse);
        when(sendResponse.isOk()).thenReturn(false);
        when(sendResponse.description()).thenReturn("Error sending message");

        // when
        telegramService.sendMessage("Test message");

        // then
        verify(telegramBot).execute(any(SendMessage.class));
        verify(sendResponse).isOk();
        verify(sendResponse).description();
    }
}
