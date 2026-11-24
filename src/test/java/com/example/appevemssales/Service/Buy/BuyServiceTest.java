package com.example.appevemssales.Service.Buy;

import com.example.appevemssales.Client.PriceGatewayClient;
import com.example.appevemssales.Repository.IBuyRepository;
import com.example.appevemssales.Repository.ITicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.model.BuyDto;
import org.openapitools.model.CreateBuyDto;
import org.openapitools.model.SectorByBatchDto;
import org.openapitools.model.SimpleEventDto;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuyServiceTest {

    private static final String AUTHORIZATION = "Bearer test-jwt";

    private IBuyRepository buyRepository;
    private ITicketRepository ticketRepository;
    private BuyMapper mapper;
    private PriceGatewayClient priceGatewayClient;
    private BuyPersistenceService persistenceService;
    private BuyService service;

    @BeforeEach
    void setUp() {
        buyRepository = mock(IBuyRepository.class);
        ticketRepository = mock(ITicketRepository.class);
        mapper = mock(BuyMapper.class);
        priceGatewayClient = mock(PriceGatewayClient.class);
        persistenceService = mock(BuyPersistenceService.class);
        service = new BuyService(
                buyRepository,
                ticketRepository,
                mapper,
                priceGatewayClient,
                persistenceService);
    }

    @Test
    void createConsumesStockAndPassesAuthenticatedUserAndTotalToTransactionService() {
        long authenticatedUserId = 7L;
        CreateBuyDto dto = createDto(42L, List.of(10L, 10L, 20L));
        BuyDto result = new BuyDto();
        stubOpenEvent(5);
        when(priceGatewayClient.consumeStock(
                42L, List.of(10L, 10L, 20L), AUTHORIZATION))
                .thenReturn(List.of(price(10L, 200), price(20L, 350)));
        when(persistenceService.persist(dto, 750, authenticatedUserId)).thenReturn(result);

        BuyDto actual = service.create(dto, authenticatedUserId, AUTHORIZATION);

        assertSame(result, actual);
        verify(priceGatewayClient).getEventById(42L, AUTHORIZATION);
        verify(ticketRepository).countByBuy_User_IdAndBuy_Event_Id(authenticatedUserId, 42L);
        verify(priceGatewayClient).consumeStock(
                42L, List.of(10L, 10L, 20L), AUTHORIZATION);
        verify(persistenceService).persist(dto, 750, authenticatedUserId);
    }

    @Test
    void createRestoresStockIfGatewayReturnsAnIncompletePriceResponse() {
        CreateBuyDto dto = createDto(42L, List.of(10L, 20L));
        stubOpenEvent(5);
        when(priceGatewayClient.consumeStock(
                42L, List.of(10L, 20L), AUTHORIZATION))
                .thenReturn(List.of(price(10L, 200)));

        assertThrows(com.example.appevecommon.Service.Exception.ResourceNotFoundException.class,
                () -> service.create(dto, 7L, AUTHORIZATION));
        verify(priceGatewayClient).releaseStock(42L, List.of(10L, 20L), AUTHORIZATION);
        verifyNoInteractions(persistenceService);
    }

    @Test
    void createRestoresStockIfGatewayReturnsDuplicateRows() {
        CreateBuyDto dto = createDto(42L, List.of(10L));
        stubOpenEvent(5);
        when(priceGatewayClient.consumeStock(42L, List.of(10L), AUTHORIZATION))
                .thenReturn(List.of(price(10L, 200), price(10L, 200)));

        assertThrows(PriceGatewayException.class,
                () -> service.create(dto, 7L, AUTHORIZATION));
        verify(priceGatewayClient).releaseStock(42L, List.of(10L), AUTHORIZATION);
        verifyNoInteractions(persistenceService);
    }

    @Test
    void createRestoresStockIfTotalDoesNotFitBuyIntegerColumn() {
        CreateBuyDto dto = createDto(42L, List.of(10L, 10L));
        stubOpenEvent(5);
        when(priceGatewayClient.consumeStock(42L, List.of(10L, 10L), AUTHORIZATION))
                .thenReturn(List.of(price(10L, Integer.MAX_VALUE)));

        assertThrows(InvalidBuyRequestException.class,
                () -> service.create(dto, 7L, AUTHORIZATION));
        verify(priceGatewayClient).releaseStock(42L, List.of(10L, 10L), AUTHORIZATION);
        verifyNoInteractions(persistenceService);
    }

    @Test
    void createRejectsPerPersonLimitBeforeConsumingStock() {
        CreateBuyDto dto = createDto(42L, List.of(10L, 20L));
        stubOpenEvent(2);
        when(ticketRepository.countByBuy_User_IdAndBuy_Event_Id(7L, 42L)).thenReturn(1L);

        assertThrows(PurchaseLimitExceededException.class,
                () -> service.create(dto, 7L, AUTHORIZATION));
        verify(priceGatewayClient, never()).consumeStock(any(), any(), any());
        verifyNoInteractions(persistenceService);
    }

    @Test
    void createRejectsInvalidAuthenticatedUserBeforeCallingOtherServices() {
        CreateBuyDto dto = createDto(42L, List.of(10L));

        assertThrows(InvalidAuthenticatedUserException.class,
                () -> service.create(dto, 0L, AUTHORIZATION));
        verifyNoInteractions(priceGatewayClient, persistenceService);
    }

    private CreateBuyDto createDto(long eventId, List<Long> ids) {
        CreateBuyDto dto = new CreateBuyDto();
        dto.setEventId(eventId);
        dto.setSectorByBatchIds(ids);
        return dto;
    }

    private void stubOpenEvent(int ticketLimit) {
        SimpleEventDto event = new SimpleEventDto();
        event.setId(42L);
        event.setArchived(false);
        event.setVisibleStart(LocalDate.now().minusDays(1));
        event.setDate(LocalDate.now().plusDays(1));
        event.setTicketsPerPersonLimit(ticketLimit);
        when(priceGatewayClient.getEventById(42L, AUTHORIZATION)).thenReturn(event);
    }

    private SectorByBatchDto price(long id, int amount) {
        SectorByBatchDto dto = new SectorByBatchDto();
        dto.setId(id);
        dto.setPrice(amount);
        return dto;
    }
}
