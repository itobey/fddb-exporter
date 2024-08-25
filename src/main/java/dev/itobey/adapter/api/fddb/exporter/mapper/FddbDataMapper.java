package dev.itobey.adapter.api.fddb.exporter.mapper;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface FddbDataMapper {

    @Mapping(target = "id", ignore = true)
    void updateFddbData(@MappingTarget FddbData target, FddbData source);

}