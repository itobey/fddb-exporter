package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * What the MCP server is and what it is sitting on top of.
 * <p>
 * The field that earns this tool its place is {@code serverDate}: an agent works from whatever it
 * believes today is, and that belief is regularly stale by a day or more. Everything a user asks
 * about their diary is relative to today, so getting it from the server once beats guessing it in
 * every single call.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerInfoDTO {

    /**
     * The running version of FDDB-Exporter, or "dev" for a build without build information.
     */
    private String appVersion;

    /**
     * Today's date as the server sees it. Every relative date parameter resolves against this.
     */
    private LocalDate serverDate;

    /**
     * The time zone the server resolves dates in.
     */
    private String timeZone;

    /**
     * Whether MongoDB persistence is on. It always is when this tool is reachable, since every tool
     * needs it - reported so the agent does not have to infer it.
     */
    private boolean mongodbEnabled;

    /**
     * Whether the daily totals are additionally written to InfluxDB. No MCP tool reads from there.
     */
    private boolean influxdbEnabled;

    /**
     * Whether new data is exported automatically on a schedule.
     */
    private boolean schedulerEnabled;

    /**
     * The cron expression of the scheduled export, absent when it is disabled.
     */
    private String schedulerCron;

    /**
     * Whether the export tools are registered on this server.
     */
    private boolean writeToolsEnabled;

    /**
     * The first day the diary holds an entry for, absent when it is empty.
     */
    private LocalDate firstEntryDate;

    /**
     * The most recent day the diary holds an entry for, absent when it is empty.
     */
    private LocalDate lastEntryDate;

    /**
     * How many days have an entry.
     */
    private long entryCount;
}
