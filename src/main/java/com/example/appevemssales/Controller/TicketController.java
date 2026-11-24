package com.example.appevemssales.Controller;

import com.example.appevecommon.Controller.BaseController;
import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevecommon.Service.Utilities.Responses.Error.ErrorResponse;
import com.example.appevecommon.Service.Utilities.Responses.Ok.PagedResponse;
import com.example.appevecommon.Service.Utilities.Responses.Response;
import com.example.appevecommon.Service.Utilities.Responses.ResponseFactory;
import com.example.appevemssales.Service.Buy.InvalidAuthenticatedUserException;
import com.example.appevemssales.Service.Security.AuthenticatedUserIdResolver;
import com.example.appevemssales.Service.Ticket.TicketFilter;
import com.example.appevemssales.Service.Ticket.TicketService;
import org.openapitools.model.TicketDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController extends BaseController<Ticket, TicketDto, TicketFilter, TicketService> {

    private final AuthenticatedUserIdResolver userIdResolver;

    public TicketController(
            TicketService service,
            AuthenticatedUserIdResolver userIdResolver) {
        super(service);
        this.userIdResolver = userIdResolver;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('Cliente')")
    public PagedResponse<TicketDto> getByFilter(@ModelAttribute TicketFilter filter) {
        if (filter == null) {
            filter = new TicketFilter();
        }
        filter.setUserId(userIdResolver.resolveCurrent());
        return ResponseFactory.ok(service.getByFilter(filter));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('Cliente')")
    public Response<TicketDto> getById(@PathVariable Long id) {
        return ResponseFactory.ok(
                service.getByIdForUser(id, userIdResolver.resolveCurrent()));
    }

    @ExceptionHandler(InvalidAuthenticatedUserException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAuthenticatedUser() {
        return ResponseFactory.unauthorized();
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }
}
