package com.example.appevemssales.Service.Ticket;

import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevecommon.Service.Utilities.ReadableMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.openapitools.model.TicketDto;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public abstract class TicketMapper implements ReadableMapper<Ticket, TicketDto> {

    @Mapping(source = "sectorByBatch.id", target = "sectorByBatchId")
    @Mapping(source = "buy.id", target = "buyId")
    public abstract TicketDto toDto(Ticket entity);
}
