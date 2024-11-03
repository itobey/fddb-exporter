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
        List<ProductWithDate> productMatches = getProductMatches(input);
        List<LocalDate> occurrenceDates = parseOccurrenceDates(input);

        CorrelationOutputDto output = new CorrelationOutputDto();
        output.setCorrelations(calculateAllCorrelations(productMatches, occurrenceDates));

        setMatchedProductsAndDates(output, productMatches);

        return output;
    }

    private List<ProductWithDate> getProductMatches(CorrelationInputDto input) {
        LocalDate startDate = input.getStartDate() != null ?
                LocalDate.parse((String) input.getStartDate()) : null;

        return mongoDBService.findByProductsWithExclusions(
                input.getInclusionKeywords(),
                input.getExclusionKeywords(),
                startDate
        );
    }

    private List<LocalDate> parseOccurrenceDates(CorrelationInputDto input) {
        return input.getOccurrenceDates().stream()
                .map(LocalDate::parse)
                .toList();
    }

    private Correlations calculateAllCorrelations(List<ProductWithDate> products, List<LocalDate> occurrenceDates) {
        Correlations correlations = new Correlations();

        correlations.setSameDay(calculateCorrelation(products, occurrenceDates, 0));
        correlations.setOneDayBefore(calculateCorrelation(products, occurrenceDates, 1));
        correlations.setTwoDaysBefore(calculateCorrelation(products, occurrenceDates, 2));

        correlations.setAcross2Days(calculateAcrossNDaysCorrelation(products,
                List.of(correlations.getSameDay(), correlations.getOneDayBefore())));
        correlations.setAcross3Days(calculateAcrossNDaysCorrelation(products,
                List.of(correlations.getSameDay(), correlations.getOneDayBefore(), correlations.getTwoDaysBefore())));

        return correlations;
    }

    private void setMatchedProductsAndDates(CorrelationOutputDto output, List<ProductWithDate> productMatches) {
        List<String> matchedProducts = productMatches.stream()
                .map(productWithDate -> productWithDate.getProduct().getName())
                .distinct()
                .toList();
        List<LocalDate> matchedDates = productMatches.stream()
                .map(ProductWithDate::getDate)
                .distinct()
                .toList();

        output.setMatchedProducts(matchedProducts);
        output.setMatchedDates(matchedDates);
        output.setAmountMatchedProducts(matchedProducts.size());
        output.setAmountMatchedDates(matchedDates.size());
    }

    private CorrelationDetail calculateAcrossNDaysCorrelation(List<ProductWithDate> products,
                                                              List<CorrelationDetail> correlations) {
        CorrelationDetail acrossDays = new CorrelationDetail();

        List<String> combinedUniqueDates = correlations.stream()
                .map(CorrelationDetail::getMatchedDates)
                .flatMap(List::stream)
                .distinct()
                .toList();

        long uniqueDatesCount = products.stream()
                .map(ProductWithDate::getDate)
                .distinct()
                .count();

        double percentage = uniqueDatesCount == 0 ? 0 :
                (double) combinedUniqueDates.size() / uniqueDatesCount * 100;

        acrossDays.setMatchedDays(combinedUniqueDates.size());
        acrossDays.setPercentage(percentage);
        acrossDays.setMatchedDates(combinedUniqueDates);

        return acrossDays;
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
