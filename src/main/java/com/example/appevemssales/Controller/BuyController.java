package com.example.appevemssales.Controller;

import com.example.appevecommon.Controller.BaseController;
import com.example.appevecommon.Models.Buy.Buy;
import com.example.appevecommon.Service.Utilities.Responses.Error.ErrorResponse;
import com.example.appevecommon.Service.Utilities.Responses.Response;
import com.example.appevecommon.Service.Utilities.Responses.ResponseFactory;
import com.example.appevemssales.Service.Buy.BuyFilter;
import com.example.appevemssales.Service.Buy.BuyService;
import com.example.appevemssales.Service.Buy.PurchaseLimitExceededException;
import com.example.appevemssales.Service.Buy.StockUnavailableException;
import com.example.appevemssales.Service.Buy.InvalidAuthenticatedUserException;
import com.example.appevemssales.Service.Buy.InvalidBuyRequestException;
import com.example.appevemssales.Service.Buy.PriceGatewayException;
import com.example.appevemssales.Service.Security.AuthenticatedUserIdResolver;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.model.BuyDto;
import org.openapitools.model.CreateBuyDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.appevecommon.Service.Utilities.Responses.Ok.PagedResponse;

@Slf4j
@RestController
@RequestMapping("/api/buys")
public class BuyController extends BaseController<Buy, BuyDto, BuyFilter, BuyService> {

    private final AuthenticatedUserIdResolver userIdResolver;

    public BuyController(BuyService service, AuthenticatedUserIdResolver userIdResolver) {
        super(service);
        this.userIdResolver = userIdResolver;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('Cliente')")
    public Response<BuyDto> create(
            @RequestBody CreateBuyDto dto,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        Long authenticatedUserId = userIdResolver.resolve(jwt);
        String userEmail = jwt.getSubject();
        log.info("Se solicitó crear una compra para el evento {} y el usuario {}",
                dto == null ? null : dto.getEventId(), authenticatedUserId);
        return ResponseFactory.resourceCreated(
                service.create(dto, authenticatedUserId,userEmail, authorizationHeader));
    }

    @ExceptionHandler(InvalidAuthenticatedUserException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAuthenticatedUser() {
        return ResponseFactory.unauthorized();
    }

    @ExceptionHandler(InvalidBuyRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBuyRequest(InvalidBuyRequestException ex) {
        return ResponseFactory.badRequest("Compra inválida", ex.getMessage());
    }

    @ExceptionHandler(PurchaseLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handlePurchaseLimitExceeded(
            PurchaseLimitExceededException ex) {
        return ResponseFactory.error(409, "Límite de compra excedido", ex.getMessage());
    }

    @ExceptionHandler(StockUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStockUnavailable(StockUnavailableException ex) {
        return ResponseFactory.error(409, "Stock insuficiente", ex.getMessage());
    }

    @ExceptionHandler(PriceGatewayException.class)
    public ResponseEntity<ErrorResponse> handlePriceGatewayFailure(PriceGatewayException ex) {
        return ResponseFactory.error(
                ex.getStatusCode(),
                "No se pudo consultar el servicio de precios",
                ex.getMessage());
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('Cliente')")
    public PagedResponse<BuyDto> getByFilter(@ModelAttribute BuyFilter filter) {
        if (filter == null) {
            filter = new BuyFilter();
        }
        filter.setUserId(userIdResolver.resolveCurrent());
        return ResponseFactory.ok(service.getByFilter(filter));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('Cliente')")
    public Response<BuyDto> getById(@PathVariable Long id) {
        return ResponseFactory.ok(
                service.getByIdForUser(id, userIdResolver.resolveCurrent()));
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return ResponseEntity.status(405).build();
    }
}
