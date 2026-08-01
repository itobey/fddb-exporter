package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of an MCP product search.
 * <p>
 * The service layer caps the number of results but does not signal that it did so. Silent
 * truncation is what makes an agent state a wrong conclusion with full confidence ("you ate this
 * exactly 50 times"), so {@code truncated} is reported explicitly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResultDTO {

    private String searchTerm;

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * The number of occurrences in this response, which is at most {@code limit}.
     */
    private int resultCount;

    private int limit;

    /**
     * Whether there are more matches than the limit allowed to be returned. When true, any count
     * derived from this response is a lower bound - narrow the range or raise the limit.
     */
    private boolean truncated;

    private List<ProductWithDateDTO> results;
}
