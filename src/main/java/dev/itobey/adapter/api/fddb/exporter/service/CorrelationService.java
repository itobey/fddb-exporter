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
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorrelationService {

    private final MongoDBService mongoDBService;

    public CorrelationOutputDto createCorrelation(CorrelationInputDto input) {
        List<ProductWithDate> productMatches = mongoDBService.findByProductsWithExclusions(
                input.getInclusionKeywords(),
                input.getExclusionKeywords(),
                switch(input.getStartDate()) {
                    case String date -> LocalDate.parse(date);
                    case null -> null;
                }
        );

        List<LocalDate> occurrenceDates = input.getOccurrenceDates().stream()
                .map(LocalDate::parse)
                .toList();

        CorrelationOutputDto output = new CorrelationOutputDto();
        Correlations correlations = new Correlations();
        output.setCorrelations(correlations);

        List<String> matchedProducts = productMatches.stream()
                .map(productWithDate -> productWithDate.getProduct().getName()).distinct().toList();
        output.setMatchedProducts(matchedProducts);
        List<LocalDate> matchedDates = productMatches.stream()
                .map(ProductWithDate::getDate).distinct().toList();
        output.setMatchedDates(matchedDates);
        output.setAmountMatchedProducts(matchedProducts.size());
        output.setAmountMatchedDates(matchedDates.size());

        // Same day correlation
        CorrelationDetail sameDayCorrelation = calculateCorrelation(productMatches, occurrenceDates, 0);
        output.getCorrelations().setSameDay(sameDayCorrelation);

        // One day prior correlation
        CorrelationDetail oneDayPriorCorrelation = calculateCorrelation(productMatches, occurrenceDates, 1);
        output.getCorrelations().setOneDayBefore(oneDayPriorCorrelation);

        // Two days prior correlation
        CorrelationDetail twoDaysPriorCorrelation = calculateCorrelation(productMatches, occurrenceDates, 2);
        output.getCorrelations().setTwoDaysBefore(twoDaysPriorCorrelation);

        CorrelationDetail across3Days = new CorrelationDetail();
        List<String> combinedUniqueDatesAcross3 = Stream.of(
                        sameDayCorrelation.getMatchedDates(),
                        oneDayPriorCorrelation.getMatchedDates(),
                        twoDaysPriorCorrelation.getMatchedDates()
                )
                .flatMap(List::stream)
                .distinct()
                .toList();
        across3Days.setMatchedDays(combinedUniqueDatesAcross3.size());
        across3Days.setPercentage(combinedUniqueDatesAcross3.size() / (double) matchedDates.size() * 100);
        across3Days.setMatchedDates(combinedUniqueDatesAcross3);
        correlations.setAcross3Days(across3Days);

        CorrelationDetail across2Days = new CorrelationDetail();
        List<String> combinedUniqueDatesAcross2 = Stream.of(
                        sameDayCorrelation.getMatchedDates(),
                        oneDayPriorCorrelation.getMatchedDates()
                )
                .flatMap(List::stream)
                .distinct()
                .toList();
        across2Days.setMatchedDays(combinedUniqueDatesAcross2.size());
        across2Days.setPercentage(combinedUniqueDatesAcross2.size() / (double) matchedDates.size() * 100);
        across2Days.setMatchedDates(combinedUniqueDatesAcross2);
        correlations.setAcross2Days(across2Days);

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

        long uniqueDatesCount = products.stream()
                .map(ProductWithDate::getDate)
                .distinct()
                .count();

        double percentage = uniqueDatesCount == 0 ? 0 :
                (double) matchedDates.size() / uniqueDatesCount * 100;

        detail.setPercentage(percentage);
        detail.setMatchedDates(matchedDates);
        detail.setMatchedDays(matchedDates.size());

        return detail;
    }

}
