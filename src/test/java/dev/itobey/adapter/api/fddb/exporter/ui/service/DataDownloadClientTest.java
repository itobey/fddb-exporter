package dev.itobey.adapter.api.fddb.exporter.ui.service;

import dev.itobey.adapter.api.fddb.exporter.dto.DownloadFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DataDownloadClientTest {

    private DataDownloadClient dataDownloadClient;

    @BeforeEach
    void setUp() {
        dataDownloadClient = new DataDownloadClient();
    }

    @Test
    void buildDownloadUrl_shouldIncludeAllParameters() {
        // given
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 12, 31);
        DownloadFormat format = DownloadFormat.CSV;
        boolean includeProducts = true;
        String decimalSeparator = "comma";

        // when
        String url = dataDownloadClient.buildDownloadUrl(fromDate, toDate, format, includeProducts, decimalSeparator);

        // then
        assertThat(url).isEqualTo("/api/v2/fddbdata/download?format=CSV&includeProducts=true&decimalSeparator=comma&fromDate=2024-01-01&toDate=2024-12-31");
    }

    @Test
    void buildDownloadUrl_shouldHandleNullDates() {
        // given
        DownloadFormat format = DownloadFormat.JSON;
        boolean includeProducts = false;
        String decimalSeparator = "dot";

        // when
        String url = dataDownloadClient.buildDownloadUrl(null, null, format, includeProducts, decimalSeparator);

        // then
        assertThat(url).isEqualTo("/api/v2/fddbdata/download?format=JSON&includeProducts=false&decimalSeparator=dot");
    }

    @Test
    void generateDownloadFilename_shouldIncludeDateRangeAndProducts() {
        // given
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 12, 31);
        DownloadFormat format = DownloadFormat.CSV;
        boolean includeProducts = true;

        // when
        String filename = DataDownloadClient.generateDownloadFilename(fromDate, toDate, format, includeProducts);

        // then
        assertThat(filename).isEqualTo("fddb-data-2024-01-01-to-2024-12-31.csv");
    }

    @Test
    void generateDownloadFilename_shouldIncludeDateRangeAndTotalsOnly() {
        // given
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 12, 31);
        DownloadFormat format = DownloadFormat.JSON;
        boolean includeProducts = false;

        // when
        String filename = DataDownloadClient.generateDownloadFilename(fromDate, toDate, format, includeProducts);

        // then
        assertThat(filename).isEqualTo("fddb-data-2024-01-01-to-2024-12-31-totals-only.json");
    }

    @Test
    void generateDownloadFilename_shouldHandleNullDatesWithProducts() {
        // given
        DownloadFormat format = DownloadFormat.CSV;
        boolean includeProducts = true;

        // when
        String filename = DataDownloadClient.generateDownloadFilename(null, null, format, includeProducts);

        // then
        assertThat(filename).isEqualTo("fddb-data-all.csv");
    }

    @Test
    void generateDownloadFilename_shouldHandleNullDatesWithTotalsOnly() {
        // given
        DownloadFormat format = DownloadFormat.JSON;
        boolean includeProducts = false;

        // when
        String filename = DataDownloadClient.generateDownloadFilename(null, null, format, includeProducts);

        // then
        assertThat(filename).isEqualTo("fddb-data-all-totals-only.json");
    }

}

