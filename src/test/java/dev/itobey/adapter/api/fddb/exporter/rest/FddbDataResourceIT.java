package dev.itobey.adapter.api.fddb.exporter.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportRequestDTO;
import dev.itobey.adapter.api.fddb.exporter.service.PersistenceService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FddbDataResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PersistenceService persistenceService;

    @Test
    @SneakyThrows
    void exportForTimerange_fromDateAfterToDate_shouldThrowException() {
        ExportRequestDTO invalidBatchExport = ExportRequestDTO.builder()
                .fromDate("2023-01-01")
                .toDate("2022-01-01")
                .build();

        mockMvc.perform(post("/api/v1/fddbdata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBatchExport)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.dateTimeError")
                        .value("The 'from' date cannot be after the 'to' date"));
    }

    @ParameterizedTest
    @CsvSource({
            "0, true",
            "0, false",
            "366, true",
            "366, false"
    })
    @SneakyThrows
    void exportForDaysBack_whenDayOutsidePermittedRange_shouldThrowException(int days, boolean includeToday) {
        mockMvc.perform(get("/api/v1/fddbdata/export?days=" + days + "&includeToday=" + includeToday))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.dateTimeError")
                        .value("Days back must be between 1 and 365"));
    }
}