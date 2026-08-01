package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * The result of an MCP product summary: everything matching one search term, rolled into one figure
 * set.
 * <p>
 * The resolved bounds are echoed for the same reason every other range tool echoes them, and
 * {@code found} distinguishes "this product was never logged" from "it was logged zero times", which
 * the bare summary with its zeroed totals does not.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryResultDTO {

    private String searchTerm;

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * Whether any product matched the search term at all.
     */
    private boolean found;

    /**
     * Only set when nothing matched, pointing at the way to find the right spelling.
     */
    private String message;

    /**
     * The aggregate. Its {@code matchedProductNames} shows which distinct names were folded into
     * these numbers - worth reading before treating them as one food.
     */
    private ProductSummaryDTO summary;
}
