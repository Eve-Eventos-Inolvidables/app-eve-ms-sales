package com.example.appevemssales.Client;

import com.example.appevecommon.Service.Exception.ResourceNotFoundException;
import com.example.appevecommon.Service.Utilities.Responses.Response;
import com.example.appevemssales.Service.Buy.InvalidBuyRequestException;
import com.example.appevemssales.Service.Buy.PriceGatewayException;
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

    private final RestClient restClient;

    public PriceGatewayClient(
            @Value("${app.gateway.base-url:http://localhost:8080}") String gatewayBaseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(gatewayBaseUrl)
                .requestFactory(requestFactory)
                .build();
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

    public List<SectorByBatchDto> getPricesForEvent(
            Long eventId,
            List<Long> uniqueSectorByBatchIds,
            String authorizationHeader) {
        try {
            Response<List<SectorByBatchDto>> response = restClient.post()
                    .uri("/app-eve-ms-events/api/precio/lookup?eventId={eventId}", eventId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(uniqueSectorByBatchIds)
                    .retrieve()
                    .body(RESPONSE_TYPE);

            if (response == null || !response.success() || response.data() == null) {
                throw new PriceGatewayException(
                        502, "El servicio de precios devolvió una respuesta inválida.");
            }
            return response.data();
        } catch (RestClientResponseException ex) {
            throw mapGatewayFailure(ex);
        } catch (RestClientException ex) {
            throw new PriceGatewayException(
                    503, "El servicio de precios no está disponible.", ex);
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
        if (remoteStatus == 401 || remoteStatus == 403) {
            return new PriceGatewayException(
                    remoteStatus, "No se autorizó la operación en el servicio de eventos.", ex);
        }
        if (remoteStatus == 409) {
            return new PriceGatewayException(
                    409, "El servicio de eventos no pudo resolver la solicitud.", ex);
        }
        return new PriceGatewayException(
                502, "No se pudo completar la operación en el servicio de eventos.", ex);
    }
}
