package com.example.appevemssales.Controller;

import com.example.appevecommon.Controller.BaseController;
import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevemssales.Service.Ticket.TicketFilter;
import com.example.appevemssales.Service.Ticket.TicketService;
import org.openapitools.model.TicketDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController extends BaseController<Ticket, TicketDto, TicketFilter, TicketService> {

    public TicketController(TicketService service) {
        super(service);
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return ResponseEntity.status(405).build();
    }
}
