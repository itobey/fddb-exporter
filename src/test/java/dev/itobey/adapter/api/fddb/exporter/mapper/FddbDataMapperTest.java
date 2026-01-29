package dev.itobey.adapter.api.fddb.exporter.mapper;

import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.testutil.TestDataLoader;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FddbDataMapperTest {

    private static final String TEST_DATA_PATH = "testdata/mapper/";

    private FddbDataMapper fddbDataMapper;

    @BeforeEach
    void setUp() {
        fddbDataMapper = Mappers.getMapper(FddbDataMapper.class);
    }

    @Test
    @SneakyThrows
    void toFddbDataDTOWithoutProducts_whenProductsPresent_shouldRemoveIdAndProducts() {
        // given
        FddbDataDTO inputDTO = TestDataLoader.loadFromJson(
                TEST_DATA_PATH + "fddb-data-dto-with-products.json", FddbDataDTO.class);

        // when
        FddbDataDTO result = fddbDataMapper.toFddbDataDTOWithoutProducts(inputDTO);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProducts()).isNull();
        assertThat(result.getDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getTotalCalories()).isEqualTo(2000.5);
        assertThat(result.getTotalFat()).isEqualTo(70.3);
        assertThat(result.getTotalCarbs()).isEqualTo(250.2);
        assertThat(result.getTotalSugar()).isEqualTo(50.1);
        assertThat(result.getTotalProtein()).isEqualTo(100.4);
        assertThat(result.getTotalFibre()).isEqualTo(30.6);
    }

    @Test
    @SneakyThrows
    void toFddbDataDTOWithoutProducts_whenProductsIsNull_shouldPreserveTotals() {
        // given
        FddbDataDTO inputDTO = TestDataLoader.loadFromJson(
                TEST_DATA_PATH + "fddb-data-dto-null-products.json", FddbDataDTO.class);

        // when
        FddbDataDTO result = fddbDataMapper.toFddbDataDTOWithoutProducts(inputDTO);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProducts()).isNull();
        assertThat(result.getDate()).isEqualTo(LocalDate.of(2024, 2, 15));
        assertThat(result.getTotalCalories()).isEqualTo(1800.0);
        assertThat(result.getTotalFat()).isEqualTo(60.0);
        assertThat(result.getTotalCarbs()).isEqualTo(220.0);
        assertThat(result.getTotalSugar()).isEqualTo(40.0);
        assertThat(result.getTotalProtein()).isEqualTo(90.0);
        assertThat(result.getTotalFibre()).isEqualTo(25.0);
    }

    @Test
    @SneakyThrows
    void toFddbDataDTOWithoutProducts_whenProductsListEmpty_shouldHandleEmptyProductsList() {
        // given
        FddbDataDTO inputDTO = TestDataLoader.loadFromJson(
                TEST_DATA_PATH + "fddb-data-dto-empty-products.json", FddbDataDTO.class);

        // when
        FddbDataDTO result = fddbDataMapper.toFddbDataDTOWithoutProducts(inputDTO);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProducts()).isNull();
        assertThat(result.getDate()).isEqualTo(LocalDate.of(2024, 3, 20));
        assertThat(result.getTotalCalories()).isEqualTo(2200.0);
    }

    @Test
    @SneakyThrows
    void toFddbDataDTOWithoutProducts_whenListProvided_shouldProcessMultipleItems() {
        // given
        List<FddbDataDTO> inputList = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-dto-list.json", FddbDataDTO.class);

        // when
        List<FddbDataDTO> result = fddbDataMapper.toFddbDataDTOWithoutProducts(inputList);

        // then
        assertThat(result).hasSize(3);

        assertThat(result.getFirst().getId()).isNull();
        assertThat(result.getFirst().getProducts()).isNull();
        assertThat(result.getFirst().getDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getFirst().getTotalCalories()).isEqualTo(2000.0);

        assertThat(result.get(1).getId()).isNull();
        assertThat(result.get(1).getProducts()).isNull();
        assertThat(result.get(1).getDate()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(result.get(1).getTotalCalories()).isEqualTo(2100.0);

        assertThat(result.get(2).getId()).isNull();
        assertThat(result.get(2).getProducts()).isNull();
        assertThat(result.get(2).getDate()).isEqualTo(LocalDate.of(2024, 1, 3));
        assertThat(result.get(2).getTotalCalories()).isEqualTo(1900.0);
    }

    @Test
    @SneakyThrows
    void toFddbDataDTOWithoutProducts_whenEmptyList_shouldHandleEmptyList() {
        // given
        List<FddbDataDTO> inputList = Collections.emptyList();

        // when
        List<FddbDataDTO> result = fddbDataMapper.toFddbDataDTOWithoutProducts(inputList);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @SneakyThrows
    void toFddbDataDTOWithoutProducts_whenPreciseValues_shouldPreserveAllNutritionValues() {
        // given
        FddbDataDTO inputDTO = TestDataLoader.loadFromJson(
                TEST_DATA_PATH + "fddb-data-dto-precise-values.json", FddbDataDTO.class);

        // when
        FddbDataDTO result = fddbDataMapper.toFddbDataDTOWithoutProducts(inputDTO);

        // then
        assertThat(result.getTotalCalories()).isEqualTo(2345.67);
        assertThat(result.getTotalFat()).isEqualTo(89.12);
        assertThat(result.getTotalCarbs()).isEqualTo(345.89);
        assertThat(result.getTotalSugar()).isEqualTo(67.34);
        assertThat(result.getTotalProtein()).isEqualTo(123.45);
        assertThat(result.getTotalFibre()).isEqualTo(45.67);
    }

    @Test
    @SneakyThrows
    void toFddbDataDTOWithoutProducts_whenZeroValues_shouldHandleZeroValues() {
        // given
        FddbDataDTO inputDTO = TestDataLoader.loadFromJson(
                TEST_DATA_PATH + "fddb-data-dto-zero-values.json", FddbDataDTO.class);

        // when
        FddbDataDTO result = fddbDataMapper.toFddbDataDTOWithoutProducts(inputDTO);

        // then
        assertThat(result.getTotalCalories()).isEqualTo(0.0);
        assertThat(result.getTotalFat()).isEqualTo(0.0);
        assertThat(result.getTotalCarbs()).isEqualTo(0.0);
        assertThat(result.getTotalSugar()).isEqualTo(0.0);
        assertThat(result.getTotalProtein()).isEqualTo(0.0);
        assertThat(result.getTotalFibre()).isEqualTo(0.0);
    }
}


