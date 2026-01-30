package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.DownloadFormat;
import dev.itobey.adapter.api.fddb.exporter.service.DataDownloadService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataDownloadResourceV2.class)
@ActiveProfiles("test")
class DataDownloadResourceV2Test {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataDownloadService dataDownloadService;

    @Test
    @SneakyThrows
    void downloadData_shouldReturnCsvWithAllParameters() {
        // given
        byte[] csvData = "Date;Calories\n2024-01-01;2000".getBytes(StandardCharsets.UTF_8);
        when(dataDownloadService.downloadData(any(LocalDate.class), any(LocalDate.class),
                eq(DownloadFormat.CSV), eq(false), eq(","))).thenReturn(csvData);

        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2024-12-31")
                        .param("format", "CSV")
                        .param("includeProducts", "false")
                        .param("decimalSeparator", "comma"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv; charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(header().string("Content-Disposition",
                        "form-data; name=\"attachment\"; filename=\"fddb-data-2024-01-01-to-2024-12-31-totals-only.csv\""))
                .andExpect(content().bytes(csvData));

        verify(dataDownloadService).downloadData(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                DownloadFormat.CSV,
                false,
                ","
        );
    }

    @Test
    @SneakyThrows
    void downloadData_shouldReturnJsonWithAllParameters() {
        // given
        byte[] jsonData = "[{\"date\":\"2024-01-01\",\"totalCalories\":2000}]".getBytes(StandardCharsets.UTF_8);
        when(dataDownloadService.downloadData(any(LocalDate.class), any(LocalDate.class),
                eq(DownloadFormat.JSON), eq(true), eq("."))).thenReturn(jsonData);

        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2024-12-31")
                        .param("format", "JSON")
                        .param("includeProducts", "true")
                        .param("decimalSeparator", "dot"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().bytes(jsonData));

        verify(dataDownloadService).downloadData(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                DownloadFormat.JSON,
                true,
                "."
        );
    }

    @Test
    @SneakyThrows
    void downloadData_shouldUseDefaultValues() {
        // given
        byte[] csvData = "Date;Calories\n2024-01-01;2000".getBytes(StandardCharsets.UTF_8);
        when(dataDownloadService.downloadData(isNull(), isNull(),
                eq(DownloadFormat.CSV), eq(false), eq(","))).thenReturn(csvData);

        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv; charset=UTF-8"));

        verify(dataDownloadService).downloadData(null, null, DownloadFormat.CSV, false, ",");
    }

    @Test
    @SneakyThrows
    void downloadData_shouldHandleNullDates() {
        // given
        byte[] csvData = "Date;Calories\n2024-01-01;2000".getBytes(StandardCharsets.UTF_8);
        when(dataDownloadService.downloadData(isNull(), isNull(),
                eq(DownloadFormat.CSV), eq(true), eq(","))).thenReturn(csvData);

        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("format", "CSV")
                        .param("includeProducts", "true")
                        .param("decimalSeparator", "comma"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "form-data; name=\"attachment\"; filename=\"fddb-data-all.csv\""));

        verify(dataDownloadService).downloadData(null, null, DownloadFormat.CSV, true, ",");
    }

    @Test
    @SneakyThrows
    void downloadData_shouldReturnBadRequest_whenFromDateIsAfterToDate() {
        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("fromDate", "2024-12-31")
                        .param("toDate", "2024-01-01")
                        .param("format", "CSV"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SneakyThrows
    void downloadData_shouldReturnBadRequest_whenInvalidDecimalSeparator() {
        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("format", "CSV")
                        .param("decimalSeparator", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SneakyThrows
    void downloadData_shouldSetCorrectContentLengthHeader() {
        // given
        byte[] csvData = "Date;Calories\n2024-01-01;2000".getBytes(StandardCharsets.UTF_8);
        when(dataDownloadService.downloadData(isNull(), isNull(),
                eq(DownloadFormat.CSV), eq(false), eq(","))).thenReturn(csvData);

        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", csvData.length));
    }

    @Test
    @SneakyThrows
    void downloadData_shouldSetCorrectFilenameForDateRange() {
        // given
        byte[] csvData = "Date;Calories\n2024-01-01;2000".getBytes(StandardCharsets.UTF_8);
        when(dataDownloadService.downloadData(any(LocalDate.class), any(LocalDate.class),
                eq(DownloadFormat.CSV), eq(false), eq(","))).thenReturn(csvData);

        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2024-12-31")
                        .param("format", "CSV")
                        .param("includeProducts", "false")
                        .param("decimalSeparator", "comma"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "form-data; name=\"attachment\"; filename=\"fddb-data-2024-01-01-to-2024-12-31-totals-only.csv\""));
    }

    @Test
    @SneakyThrows
    void downloadData_shouldSetCorrectFilenameWithProducts() {
        // given
        byte[] jsonData = "[{\"date\":\"2024-01-01\"}]".getBytes(StandardCharsets.UTF_8);
        when(dataDownloadService.downloadData(any(LocalDate.class), any(LocalDate.class),
                eq(DownloadFormat.JSON), eq(true), eq("."))).thenReturn(jsonData);

        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2024-12-31")
                        .param("format", "JSON")
                        .param("includeProducts", "true")
                        .param("decimalSeparator", "dot"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "form-data; name=\"attachment\"; filename=\"fddb-data-2024-01-01-to-2024-12-31.json\""));
    }

    @Test
    @SneakyThrows
    void downloadData_shouldHandleInvalidDateFormat() {
        // when & then
        mockMvc.perform(get("/api/v2/fddbdata/download")
                        .param("fromDate", "invalid-date")
                        .param("format", "CSV"))
                .andExpect(status().isBadRequest());
    }
}



