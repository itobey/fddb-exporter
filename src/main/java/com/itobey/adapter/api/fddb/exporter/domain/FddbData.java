package com.itobey.adapter.api.fddb.exporter.domain;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
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
    private Date date;
    private int kcal;
    private int fat;
    private int carbs;
    private int sugar;
    private int protein;
    private int fiber;

}
