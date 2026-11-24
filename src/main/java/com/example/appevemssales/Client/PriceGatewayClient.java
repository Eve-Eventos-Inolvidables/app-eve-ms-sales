package com.example.appevemssales.Client;

import com.example.appevecommon.Service.Exception.ResourceNotFoundException;
import com.example.appevecommon.Service.Utilities.Responses.Response;
import com.example.appevemssales.Service.Buy.InvalidBuyRequestException;
import com.example.appevemssales.Service.Buy.PriceGatewayException;
import com.example.appevemssales.Service.Buy.StockUnavailableException;
import org.openapitools.model.SimpleEventDto;
import org.openapitools.model.SectorByBatchDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class PriceGatewayClient {

    private static final ParameterizedTypeReference<Response<List<SectorByBatchDto>>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Response<SimpleEventDto>> EVENT_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Response<Void>> EMPTY_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String stockCommandKey;

    public PriceGatewayClient(
            @Value("${app.gateway.base-url:http://localhost:8080}") String gatewayBaseUrl,
            @Value("${app.events.stock-command-key:}") String stockCommandKey) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(gatewayBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.stockCommandKey = stockCommandKey;
    }

    public SimpleEventDto getEventById(Long eventId, String authorizationHeader) {
        try {
            Response<SimpleEventDto> response = restClient.get()
                    .uri("/app-eve-ms-events/api/sevent/{eventId}", eventId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(EVENT_RESPONSE_TYPE);

            if (response == null || !response.success() || response.data() == null) {
                throw new PriceGatewayException(
                        502, "El servicio de eventos devolvió una respuesta inválida.");
            }
            return response.data();
        } catch (RestClientResponseException ex) {
            throw mapGatewayFailure(ex);
        } catch (RestClientException ex) {
            throw new PriceGatewayException(
                    503, "El servicio de eventos no está disponible.", ex);
        }
    }

    public List<SectorByBatchDto> consumeStock(
            Long eventId,
            List<Long> selectedSectorByBatchIds,
            String authorizationHeader) {
        ensureStockCommandKeyConfigured();
        try {
            Response<List<SectorByBatchDto>> response = restClient.post()
                    .uri("/app-eve-ms-events/api/precio/consume?eventId={eventId}", eventId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .header("X-Sales-Stock-Key", stockCommandKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(selectedSectorByBatchIds)
                    .retrieve()
                    .body(RESPONSE_TYPE);

            if (response == null || !response.success() || response.data() == null) {
                throw new PriceGatewayException(
                        502, "El servicio de stock devolvió una respuesta inválida.");
            }
            return response.data();
        } catch (RestClientResponseException ex) {
            throw mapGatewayFailure(ex);
        } catch (RestClientException ex) {
            throw new PriceGatewayException(
                    503, "El servicio de stock no está disponible.", ex);
        }
    }

    public void releaseStock(
            Long eventId,
            List<Long> selectedSectorByBatchIds,
            String authorizationHeader) {
        ensureStockCommandKeyConfigured();
        try {
            Response<Void> response = restClient.post()
                    .uri("/app-eve-ms-events/api/precio/release?eventId={eventId}", eventId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .header("X-Sales-Stock-Key", stockCommandKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(selectedSectorByBatchIds)
                    .retrieve()
                    .body(EMPTY_RESPONSE_TYPE);

            if (response == null || !response.success()) {
                throw new PriceGatewayException(
                        502, "El servicio de stock no confirmó la compensación.");
            }
        } catch (RestClientResponseException ex) {
            throw mapGatewayFailure(ex);
        } catch (RestClientException ex) {
            throw new PriceGatewayException(
                    503, "No se pudo contactar al servicio para compensar el stock.", ex);
        }
    }

    private void ensureStockCommandKeyConfigured() {
        if (stockCommandKey == null || stockCommandKey.isBlank()) {
            throw new PriceGatewayException(
                    503, "Falta configurar la credencial interna para modificar stock.");
        }
    }

    private RuntimeException mapGatewayFailure(RestClientResponseException ex) {
        int remoteStatus = ex.getStatusCode().value();
        if (remoteStatus == 400) {
            return new InvalidBuyRequestException(
                    "El servicio de eventos rechazó la selección.");
        }
        if (remoteStatus == 404) {
            return new ResourceNotFoundException(
                    "No se encontró el evento o uno de los precios solicitados.");
        }
        if (remoteStatus == 409) {
            return new StockUnavailableException(
                    "No hay stock suficiente para una o más selecciones.");
        }
        if (remoteStatus == 401 || remoteStatus == 403) {
            return new PriceGatewayException(
                    remoteStatus, "No se autorizó la operación en el servicio de eventos.", ex);
        }
        return new PriceGatewayException(
                502, "No se pudo completar la operación en el servicio de eventos.", ex);
    }
}
