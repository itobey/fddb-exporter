package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationDetail;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.Correlations;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.MongoDBService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorrelationService {

    private final MongoDBService mongoDBService;

    public CorrelationOutputDto createCorrelation(CorrelationInputDto input) {
        List<ProductWithDate> productMatches = mongoDBService.findByProductsWithExclusions(
                input.getInclusionKeywords(),
                input.getExclusionKeywords()
        );

        List<LocalDate> occurrenceDates = input.getOccurrenceDates().stream()
                .map(LocalDate::parse)
                .toList();

        CorrelationOutputDto output = new CorrelationOutputDto();
        Correlations correlations = new Correlations();
        output.setCorrelations(correlations);

        // Same day correlation
        CorrelationDetail sameDayCorrelation = calculateCorrelation(productMatches, occurrenceDates, 0);
        output.getCorrelations().setSameDay(sameDayCorrelation);

        // One day prior correlation
        CorrelationDetail oneDayPriorCorrelation = calculateCorrelation(productMatches, occurrenceDates, 1);
        output.getCorrelations().setOneDayBefore(oneDayPriorCorrelation);

        // Two days prior correlation
        CorrelationDetail twoDaysPriorCorrelation = calculateCorrelation(productMatches, occurrenceDates, 2);
        output.getCorrelations().setTwoDaysBefore(twoDaysPriorCorrelation);

        return output;
    }

    private CorrelationDetail calculateCorrelation(List<ProductWithDate> products, List<LocalDate> occurrenceDates, int daysOffset) {
        CorrelationDetail detail = new CorrelationDetail();
        List<String> matchedDates = new ArrayList<>();

        for (LocalDate occurrenceDate : occurrenceDates) {
            LocalDate targetDate = occurrenceDate.minusDays(daysOffset);
            boolean hasMatch = products.stream()
                    .anyMatch(product -> product.getDate().equals(targetDate));

            if (hasMatch) {
                matchedDates.add(targetDate.toString());
            }
        }

        double percentage = occurrenceDates.isEmpty() ? 0 :
                (double) matchedDates.size() / occurrenceDates.size() * 100;

        detail.setPercentage(percentage);
        detail.setMatchedDates(matchedDates);

        return detail;
    }


}
