package com.example.appevemssales.Service.Buy;

import com.example.appevecommon.Models.Buy.Buy;
import com.example.appevecommon.Service.Utilities.ReadableMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.openapitools.model.BuyDto;
import org.openapitools.model.CreateBuyDto;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public abstract class BuyMapper implements ReadableMapper<Buy, BuyDto> {

    @Mapping(source = "event.id", target = "eventId")
    @Mapping(source = "user.id", target = "userId")
    public abstract BuyDto toDto(Buy entity);

    @Mapping(source = "eventId", target = "event.id")
    @Mapping(source = "userId", target = "user.id")
    public abstract Buy toEntity(CreateBuyDto dto);
}
