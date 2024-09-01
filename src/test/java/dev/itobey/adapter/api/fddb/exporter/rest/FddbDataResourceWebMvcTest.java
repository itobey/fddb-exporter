package dev.itobey.adapter.api.fddb.exporter.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportRequestDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FddbDataResource.class)
class FddbDataResourceWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FddbDataService fddbDataService;

    @Test
    @SneakyThrows
    void exportForTimerange_InvalidInput() {
        ExportRequestDTO invalidBatchExport = ExportRequestDTO.builder()
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