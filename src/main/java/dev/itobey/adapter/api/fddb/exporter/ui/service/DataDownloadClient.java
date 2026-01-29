package dev.itobey.adapter.api.fddb.exporter.ui.service;

import dev.itobey.adapter.api.fddb.exporter.dto.DownloadFormat;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

/**
 * Client service for data download API endpoints.
 * Used by the Vaadin UI to interact with the REST API.
 */
@Service
@Slf4j
public class DataDownloadClient {

    private static final String DOWNLOAD_URL = "/api/v2/fddbdata/download";

    /**
     * Builds the download URL with the specified parameters.
     * Uses a relative URL so it works regardless of where the application is deployed.
     *
     * @param fromDate         optional start date
     * @param toDate           optional end date
     * @param format           the download format
     * @param includeProducts  whether to include product details
     * @param decimalSeparator the decimal separator for CSV format
     * @return the complete download URL
     */
    public String buildDownloadUrl(LocalDate fromDate, LocalDate toDate, DownloadFormat format, boolean includeProducts, String decimalSeparator) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(DOWNLOAD_URL)
                .queryParam("format", format.name())
                .queryParam("includeProducts", includeProducts)
                .queryParam("decimalSeparator", decimalSeparator);

        if (fromDate != null) {
            builder.queryParam("fromDate", fromDate.toString());
        }
        if (toDate != null) {
            builder.queryParam("toDate", toDate.toString());
        }

        return builder.build().encode().toUriString();
    }

    /**
     * Generates a filename for the download.
     *
     * @param fromDate        optional start date
     * @param toDate          optional end date
     * @param format          the download format
     * @param includeProducts whether to include product details
     * @return the generated filename
     */
    @NonNull
    public static String generateDownloadFilename(LocalDate fromDate, LocalDate toDate, DownloadFormat format, boolean includeProducts) {
        StringBuilder filename = new StringBuilder("fddb-data");

        if (fromDate != null || toDate != null) {
            filename.append("-");
            if (fromDate != null) {
                filename.append(fromDate);
            }
            filename.append("-to-");
            if (toDate != null) {
                filename.append(toDate);
            }
        } else {
            filename.append("-all");
        }

        if (!includeProducts) {
            filename.append("-totals-only");
        }

        filename.append(format.getFileExtension());
        return filename.toString();
    }
}

