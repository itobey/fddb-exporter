package dev.itobey.adapter.api.fddb.exporter.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Contains the data retrieved from FDDB to a specific timeframe.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class FddbData {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date date;
    private int kcal;
    private int fat;
    private int carbs;
    private int sugar;
    private int protein;
    private int fiber;

}
