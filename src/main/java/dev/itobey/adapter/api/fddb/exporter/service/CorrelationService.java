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
        LocalDate startDate = (input.getStartDate() != null && !input.getStartDate().isEmpty()) ?
                LocalDate.parse(input.getStartDate()) : null;

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

        List<String> filteredDates = new ArrayList<>();
        List<String> allDates = correlations.stream()
                .map(CorrelationDetail::getMatchedDates)
                .flatMap(List::stream)
                .sorted()
                .distinct()
                .toList();

        for (String date : allDates) {
            boolean hasConsecutiveDate = isHasConsecutiveDate(correlations, date, filteredDates);

            if (!hasConsecutiveDate) {
                filteredDates.add(date);
            }
        }

        long uniqueDatesCount = products.stream()
                .map(ProductWithDate::getDate)
                .distinct()
                .count();

        List<String> filteredDistinctDates = filteredDates.stream().distinct().toList();
        double percentage = uniqueDatesCount == 0 ? 0 :
                (double) filteredDistinctDates.size() / uniqueDatesCount * 100;

        CorrelationDetail acrossDays = new CorrelationDetail();
        acrossDays.setMatchedDays(filteredDistinctDates.size());
        acrossDays.setPercentage(percentage);
        acrossDays.setMatchedDates(allDates);

        return acrossDays;
    }

    private static boolean isHasConsecutiveDate(List<CorrelationDetail> correlations, String date, List<String> filteredDates) {
        LocalDate currentDate = LocalDate.parse(date);
        boolean hasConsecutiveDate = false;

        // Check for consecutive dates based on correlation size (2 or 3 days)
        if (correlations.size() == 2) {
            hasConsecutiveDate = filteredDates.contains(currentDate.minusDays(1).toString());
        } else if (correlations.size() == 3) {
            hasConsecutiveDate = filteredDates.contains(currentDate.minusDays(1).toString())
                    || filteredDates.contains(currentDate.minusDays(2).toString());
        }
        return hasConsecutiveDate;
    }


    private CorrelationDetail calculateCorrelation(List<ProductWithDate> products, List<LocalDate> occurrenceDates, int daysOffset) {
        List<String> matchedDates = occurrenceDates.stream()
                .map(occurrenceDate -> occurrenceDate.minusDays(daysOffset))
                .filter(targetDate -> products.stream()
                        .anyMatch(product -> product.getDate().equals(targetDate)))
                .map(LocalDate::toString)
                .distinct()
                .toList();

        long uniqueDatesCount = products.stream()
                .map(ProductWithDate::getDate)
                .distinct()
                .count();

        double percentage = uniqueDatesCount == 0 ? 0 :
                (double) matchedDates.size() / uniqueDatesCount * 100;

        CorrelationDetail detail = new CorrelationDetail();
        detail.setPercentage(percentage);
        detail.setMatchedDates(matchedDates);
        detail.setMatchedDays(matchedDates.size());

        return detail;
    }

}
