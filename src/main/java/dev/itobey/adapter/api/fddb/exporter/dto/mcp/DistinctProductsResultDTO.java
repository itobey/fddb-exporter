package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The result of an MCP vocabulary lookup: the distinct product names the diary actually contains.
 * <p>
 * This exists so an agent can resolve the user's wording ("oats") to the spelling FDDB stores
 * ("Alnatura Haferflocken kernig") before searching, instead of guessing and getting nothing back.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistinctProductsResultDTO {

    /**
     * The substring the names were filtered by, absent when all names were requested.
     */
    private String searchTerm;

    private int resultCount;

    private int limit;

    /**
     * Whether more names exist than were returned.
     */
    private boolean truncated;

    private List<String> names;
}
