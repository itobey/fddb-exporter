package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.Correlations;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.MongoDBService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationServiceTest {

    @InjectMocks
    private CorrelationService correlationService;

    @Mock
    private MongoDBService mongoDBService;

    @Test
    void createCorrelation_shouldReturnFullyPopulatedCorrelationOutput() {
        // Given
        CorrelationInputDto input = new CorrelationInputDto();
        input.setInclusionKeywords(List.of("pizza"));
        input.setExclusionKeywords(List.of("pineapple"));
        List<String> occurrenceDates = Arrays.asList("2024-01-10", "2024-02-10", "2024-03-10", "2024-04-10",
                "2024-05-10", "2024-06-10", "2024-07-10", "2024-08-10",
                "2024-09-10", "2024-10-10", "2024-11-10", "2024-12-10");
        input.setOccurrenceDates(occurrenceDates);

        List<ProductWithDate> mockProducts = createProductsWithDates(
                "pizza",
                "2024-01-10", // matches the same day
                "2024-02-10", // matches the same day
                "2024-03-10", // matches the same day
                "2024-01-02", // does not match
                "2024-04-09", // matches the day prior
                "2024-08-08", // matches 2 days prior
                "2024-09-08" // matches 2 days prior
        );

        when(mongoDBService.findByProductsWithExclusions(
                anyList(),
                anyList(),
                any()
        )).thenReturn(mockProducts);

        // When
        CorrelationOutputDto result = correlationService.createCorrelation(input);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMatchedProducts()).containsExactly("pizza");
        assertThat(result.getMatchedDates()).containsExactly(
                LocalDate.of(2024, 1, 10),  // matches the same day
                LocalDate.of(2024, 2, 10),  // matches the same day
                LocalDate.of(2024, 3, 10),  // matches the same day
                LocalDate.of(2024, 1, 2),  // does not match
                LocalDate.of(2024, 4, 9),  // matches the day prior
                LocalDate.of(2024, 8, 8),  // matches 2 days prior
                LocalDate.of(2024, 9, 8)   // matches 2 days prior

        );
        assertThat(result.getAmountMatchedProducts()).isEqualTo(1);
        assertThat(result.getAmountMatchedDates()).isEqualTo(7);

        // Verify correlations
        Correlations correlations = result.getCorrelations();
        assertThat(correlations.getSameDay()).satisfies(sameDay -> {
            assertThat(sameDay.getPercentage()).isEqualTo(42.86);
            assertThat(sameDay.getMatchedDates()).hasSize(3);
            assertThat(sameDay.getMatchedDays()).isEqualTo(3);
        });

        assertThat(correlations.getOneDayBefore()).satisfies(oneDayBefore -> {
            assertThat(oneDayBefore.getPercentage()).isEqualTo(14.29);
            assertThat(oneDayBefore.getMatchedDates()).hasSize(1);
            assertThat(oneDayBefore.getMatchedDays()).isEqualTo(1);
        });

        assertThat(correlations.getTwoDaysBefore()).satisfies(twoDaysBefore -> {
            assertThat(twoDaysBefore.getPercentage()).isEqualTo(28.57);
            assertThat(twoDaysBefore.getMatchedDates()).hasSize(2);
            assertThat(twoDaysBefore.getMatchedDays()).isEqualTo(2);
        });

        assertThat(correlations.getAcross2Days()).satisfies(across2Days -> {
            assertThat(across2Days.getPercentage()).isEqualTo(57.14);
            assertThat(across2Days.getMatchedDates()).hasSize(4);
            assertThat(across2Days.getMatchedDays()).isEqualTo(4);
        });

        assertThat(correlations.getAcross3Days()).satisfies(across3Days -> {
            assertThat(across3Days.getPercentage()).isEqualTo(85.71);
            assertThat(across3Days.getMatchedDates()).hasSize(6);
            assertThat(across3Days.getMatchedDays()).isEqualTo(6);
        });

        verify(mongoDBService).findByProductsWithExclusions(
                input.getInclusionKeywords(),
                input.getExclusionKeywords(),
                null
        );
    }


    @Test
    @Disabled
        // TODO: fix this test
    void createCorrelation_WithNoMatches_ReturnsZeroCorrelations() {
        // Given
        CorrelationInputDto input = new CorrelationInputDto();
        input.setInclusionKeywords(Collections.singletonList("NonExistentFood"));
        input.setExclusionKeywords(Collections.emptyList());
        input.setOccurrenceDates(Collections.singletonList("2024-03-15"));

        when(mongoDBService.findByProductsWithExclusions(anyList(), anyList(), null)).thenReturn(Collections.emptyList());

        // When
        CorrelationOutputDto result = correlationService.createCorrelation(input);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCorrelations()).isNotNull();
        assertThat(result.getCorrelations().getSameDay().getPercentage()).isZero();
        assertThat(result.getCorrelations().getOneDayBefore().getPercentage()).isZero();
        assertThat(result.getCorrelations().getTwoDaysBefore().getPercentage()).isZero();
    }

    private List<ProductWithDate> createProductsWithDates(String productName, String... dates) {
        return Arrays.stream(dates)
                .map(date -> {
                    ProductWithDate productWithDate = new ProductWithDate();
                    productWithDate.setDate(LocalDate.parse(date));
                    Product product = new Product();
                    product.setName(productName);
                    productWithDate.setProduct(product);
                    return productWithDate;
                })
                .toList();
    }


}
