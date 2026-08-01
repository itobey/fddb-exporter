package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductRanking;
import dev.itobey.adapter.api.fddb.exporter.dto.TopProductDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of an MCP "top products" ranking.
 * <p>
 * As with the product search, the aggregation itself cuts off at the limit without saying so, and
 * "these are my most-eaten products" is exactly the kind of claim an agent should not make from a
 * silently truncated list - hence the explicit {@code truncated} flag.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopProductsResultDTO {

    /**
     * The criterion the products are ranked by, echoed so the agent cannot mistake a calorie
     * ranking for a frequency ranking.
     */
    private ProductRanking rankedBy;

    private LocalDate fromDate;

    private LocalDate toDate;

    private int resultCount;

    private int limit;

    /**
     * Whether more products matched than the limit allowed to be returned.
     */
    private boolean truncated;

    private List<TopProductDTO> results;
}
