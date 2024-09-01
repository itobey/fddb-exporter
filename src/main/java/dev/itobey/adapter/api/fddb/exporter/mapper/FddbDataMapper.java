package dev.itobey.adapter.api.fddb.exporter.mapper;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FddbDataMapper {

    @Mapping(target = "id", ignore = true)
    void updateFddbData(@MappingTarget FddbData target, FddbData source);

    FddbDataDTO toFddbDataDTO(FddbData fddbData);

    List<FddbDataDTO> toFddbDataDTO(List<FddbData> fddbData);

    ProductWithDateDTO toProductWithDateDto(ProductWithDate product);

    List<ProductWithDateDTO> toProductWithDateDto(List<ProductWithDate> product);

}