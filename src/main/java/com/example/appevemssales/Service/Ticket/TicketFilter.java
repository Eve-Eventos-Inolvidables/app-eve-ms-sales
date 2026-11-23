package com.example.appevemssales.Service.Ticket;

import com.example.appevecommon.Service.Utilities.Responses.PagedFilter;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketFilter extends PagedFilter {
    private Long buyId;
    private Long sectorByBatchId;
}
