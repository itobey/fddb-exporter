package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.RollingAveragePreset;
import dev.itobey.adapter.api.fddb.exporter.domain.UserSettings;
import dev.itobey.adapter.api.fddb.exporter.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @InjectMocks
    private UserSettingsService userSettingsService;

    @Test
    void saveSettings_shouldPersistWithTheSingletonId() {
        // given
        UserSettings settings = new UserSettings();
        settings.setId("something-else");
        settings.getRollingAveragePresets().add(preset("Q1 2025"));

        // when
        userSettingsService.saveSettings(settings);

        // then
        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(userSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(UserSettings.DEFAULT_SETTINGS_ID);
        assertThat(captor.getValue().getRollingAveragePresets()).hasSize(1);
    }

    @Test
    void saveSettings_shouldPropagateRepositoryFailure() {
        // given
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenThrow(new DataAccessResourceFailureException("mongo down"));

        // when / then
        assertThatThrownBy(() -> userSettingsService.saveSettings(new UserSettings()))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessageContaining("mongo down");
    }

    @Test
    void getSettings_shouldReturnStoredSettings() {
        // given
        UserSettings stored = new UserSettings();
        stored.getRollingAveragePresets().add(preset("Q1 2025"));
        when(userSettingsRepository.findById(UserSettings.DEFAULT_SETTINGS_ID)).thenReturn(Optional.of(stored));

        // when
        UserSettings result = userSettingsService.getSettings();

        // then
        assertThat(result).isSameAs(stored);
    }

    @Test
    void getSettings_shouldCreateDefaultsWhenNoneStored() {
        // given
        when(userSettingsRepository.findById(UserSettings.DEFAULT_SETTINGS_ID)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserSettings result = userSettingsService.getSettings();

        // then
        assertThat(result.getId()).isEqualTo(UserSettings.DEFAULT_SETTINGS_ID);
        assertThat(result.getRollingAveragePresets()).isEmpty();
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    void getSettings_shouldFallBackToDefaultsWhenReadFails() {
        // given
        when(userSettingsRepository.findById(UserSettings.DEFAULT_SETTINGS_ID))
                .thenThrow(new DataAccessResourceFailureException("mongo down"));

        // when
        UserSettings result = userSettingsService.getSettings();

        // then - the read fallback is deliberate so views still render; nothing is persisted
        assertThat(result.getRollingAveragePresets()).isEmpty();
        verify(userSettingsRepository, never()).save(any(UserSettings.class));
    }

    @Test
    void getSettings_shouldFallBackToDefaultsWhenCreatingDefaultsFails() {
        // given
        when(userSettingsRepository.findById(UserSettings.DEFAULT_SETTINGS_ID)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenThrow(new DataAccessResourceFailureException("mongo down"));

        // when
        UserSettings result = userSettingsService.getSettings();

        // then
        assertThat(result.getRollingAveragePresets()).isEmpty();
    }

    private RollingAveragePreset preset(String name) {
        RollingAveragePreset preset = new RollingAveragePreset();
        preset.setName(name);
        preset.setFromDate(LocalDate.of(2025, 1, 1));
        preset.setToDate(LocalDate.of(2025, 3, 31));
        return preset;
    }
}
