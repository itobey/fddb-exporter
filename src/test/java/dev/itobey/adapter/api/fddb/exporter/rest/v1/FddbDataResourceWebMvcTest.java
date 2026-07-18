package dev.itobey.adapter.api.fddb.exporter.rest.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.service.DataMigrationService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc test for deprecated v1 API compatibility.
 *
 * @deprecated Tests deprecated v1 API. Create new tests for v2 controllers.
 */
@Deprecated
@WebMvcTest(FddbDataResourceV1.class)
@AutoConfigureJsonTesters
@Tag("v1-compat")
class FddbDataResourceWebMvcTest {

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
    private FddbDataService fddbDataService;
    @MockitoBean
    private DataMigrationService dataMigrationService;

    @Test
    @SneakyThrows
    void exportForTimerange_InvalidInput() {
        DateRangeDTO invalidBatchExport = DateRangeDTO.builder()
                .fromDate("2023/01/01")  // Invalid format
                .toDate(null)  // Null value
                .build();

        mockMvc.perform(post("/api/v1/fddbdata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBatchExport)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fromDate").value("From date must be in the format YYYY-MM-DD"))
                .andExpect(jsonPath("$.toDate").value("To date cannot be null"));
    }
}