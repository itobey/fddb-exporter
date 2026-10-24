package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ExportInProgressException;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc test for v2 Export API.
 */
@WebMvcTest(FddbDataExportResourceV2.class)
@AutoConfigureJsonTesters
@Tag("v2")
@SuppressWarnings("deprecation")
class FddbDataExportResourceV2WebMvcTest {

    @TestConfiguration
    static class ObjectMapperConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    @SuppressWarnings("unused")
    private FddbDataService fddbDataService;

    @Test
    void progressConflictKeepsTheProblemResponseBeforeStreaming() throws Exception {
        when(fddbDataService.exportForTimerange(any(),
                any()))
                .thenThrow(new ExportInProgressException("Another export is already running"));
        mockMvc.perform(post("/api/v2/fddbdata/export/progress")
                        .contentType(MediaType.APPLICATION_JSON).accept("application/x-ndjson")
                        .content("{\"fromDate\":\"2026-10-01\",\"toDate\":\"2026-10-02\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void progressFailureAfterOneDaySendsATerminalError() throws Exception {
        when(fddbDataService.exportForTimerange(any(),
                any())).thenAnswer(call -> {
            BiConsumer<LocalDate, Boolean> onDay = call.getArgument(1);
            onDay.accept(LocalDate.parse("2026-10-01"), true);
            throw new AuthenticationException("test");
        });
        String body = mockMvc.perform(post("/api/v2/fddbdata/export/progress")
                        .contentType(MediaType.APPLICATION_JSON).accept("application/x-ndjson")
                        .content("{\"fromDate\":\"2026-10-01\",\"toDate\":\"2026-10-02\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var events = body.lines().toList();
        assertThat(events).hasSize(2);
        assertThat(objectMapper.readTree(events.get(0)).get("date").asText())
                .isEqualTo("2026-10-01");
        assertThat(objectMapper.readTree(events.get(1)).get("complete").asBoolean()).isTrue();
        assertThat(objectMapper.readTree(events.get(1)).get("error").asText()).contains("Sync stopped");
    }

    @Test
    @SneakyThrows
    void exportForTimerange_InvalidInput() {
        DateRangeDTO invalidBatchExport = DateRangeDTO.builder()
                .fromDate("2023/01/01")  // Invalid format
                .toDate(null)  // Null value
                .build();

        mockMvc.perform(post("/api/v2/fddbdata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBatchExport)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.fromDate").value("From date must be in the format YYYY-MM-DD"))
                .andExpect(jsonPath("$.errors.toDate").value("To date cannot be null"));
    }
}

