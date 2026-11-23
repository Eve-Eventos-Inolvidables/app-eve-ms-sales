package com.example.appevemssales.Controller;

import com.example.appevecommon.Controller.BaseController;
import com.example.appevecommon.Models.Buy.Buy;
import com.example.appevecommon.Service.Utilities.Responses.Error.ErrorResponse;
import com.example.appevecommon.Service.Utilities.Responses.Response;
import com.example.appevecommon.Service.Utilities.Responses.ResponseFactory;
import com.example.appevemssales.Service.Buy.BuyFilter;
import com.example.appevemssales.Service.Buy.BuyService;
import com.example.appevemssales.Service.Buy.InvalidBuyRequestException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.model.BuyDto;
import org.openapitools.model.CreateBuyDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/buys")
public class BuyController extends BaseController<Buy, BuyDto, BuyFilter, BuyService> {

    public BuyController(BuyService service) {
        super(service);
    }

    @PostMapping
    public Response<BuyDto> create(@Valid @RequestBody CreateBuyDto dto) {
        log.info("Se solicitó crear una compra para el evento {} y el usuario {}",
                dto.getEventId(), dto.getUserId());
        return ResponseFactory.resourceCreated(service.create(dto));
    }

    @ExceptionHandler(InvalidBuyRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBuyRequest(InvalidBuyRequestException ex) {
        return ResponseFactory.badRequest("Compra inválida", ex.getMessage());
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return ResponseEntity.status(405).build();
    }
}
