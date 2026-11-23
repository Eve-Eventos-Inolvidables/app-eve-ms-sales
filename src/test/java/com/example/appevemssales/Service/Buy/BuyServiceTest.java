package com.example.appevemssales.Service.Buy;

import com.example.appevecommon.Models.Buy.Buy;
import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevecommon.Models.Event.Batch.Batch;
import com.example.appevecommon.Models.Event.Batch.SectorByBatch;
import com.example.appevecommon.Models.Event.SimpleEvent;
import com.example.appevecommon.Models.Event.Sector.Sector;
import com.example.appevecommon.Models.User.User;
import com.example.appevecommon.Service.Exception.ResourceNotFoundException;
import com.example.appevemssales.Repository.IBuyRepository;
import com.example.appevemssales.Repository.ISectorByBatchRepository;
import com.example.appevemssales.Repository.ITicketRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.model.BuyDto;
import org.openapitools.model.CreateBuyDto;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

class BuyServiceTest {

    private IBuyRepository buyRepository;
    private ITicketRepository ticketRepository;
    private ISectorByBatchRepository sectorByBatchRepository;
    private BuyMapper mapper;
    private EntityManager entityManager;
    private BuyService service;

    @BeforeEach
    void setUp() {
        buyRepository = mock(IBuyRepository.class);
        ticketRepository = mock(ITicketRepository.class);
        sectorByBatchRepository = mock(ISectorByBatchRepository.class);
        mapper = mock(BuyMapper.class);
        entityManager = mock(EntityManager.class);

        service = new BuyService(
                buyRepository,
                ticketRepository,
                sectorByBatchRepository,
                mapper);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    void createCalculatesServerTotalAndCreatesOneTicketPerSelection() {
        long eventId = 42L;
        long userId = 7L;
        CreateBuyDto dto = createDto(eventId, userId, List.of(10L, 10L, 20L));
        SectorByBatch first = sectorByBatch(10L, 200, eventId);
        SectorByBatch second = sectorByBatch(20L, 350, eventId);
        SimpleEvent event = new SimpleEvent();
        event.setId(eventId);
        User user = new User();
        user.setId(userId);

        when(sectorByBatchRepository.findAllById(anyIterable()))
                .thenReturn(List.of(first, second));
        when(entityManager.find(SimpleEvent.class, eventId)).thenReturn(event);
        when(entityManager.find(User.class, userId)).thenReturn(user);
        when(mapper.toEntity(dto)).thenReturn(new Buy());
        when(buyRepository.save(any(Buy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toDto(any(Buy.class))).thenReturn(new BuyDto());
        List<Ticket> createdTickets = new ArrayList<>();
        when(ticketRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            Iterable<Ticket> requestedTickets = invocation.getArgument(0);
            requestedTickets.forEach(createdTickets::add);
            return createdTickets;
        });

        BuyDto result = service.create(dto);

        assertNotNull(result);
        var buyCaptor = org.mockito.ArgumentCaptor.forClass(Buy.class);
        verify(buyRepository).save(buyCaptor.capture());
        Buy savedBuy = buyCaptor.getValue();
        assertEquals(750, savedBuy.getTotal());
        assertNotNull(savedBuy.getDate());
        assertSame(event, savedBuy.getEvent());
        assertSame(user, savedBuy.getUser());

        verify(ticketRepository).saveAll(anyIterable());
        assertEquals(3, createdTickets.size());
        assertEquals(2, createdTickets.stream()
                .filter(ticket -> ticket.getSectorByBatch().getId().equals(10L))
                .count());
        assertTrue(createdTickets.stream().allMatch(ticket -> ticket.getBuy() == savedBuy));
        assertTrue(createdTickets.stream().allMatch(ticket -> ticket.getQrToken() != null));
        assertEquals(3, createdTickets.stream().map(Ticket::getQrToken).distinct().count());
    }

    @Test
    void createRejectsUnknownOrArchivedSectorByBatchBeforeSaving() {
        CreateBuyDto dto = createDto(42L, 7L, List.of(404L));
        when(sectorByBatchRepository.findAllById(anyIterable())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.create(dto));
        verifyNoInteractions(buyRepository, ticketRepository);
    }

    @Test
    void createRejectsSectorByBatchFromAnotherEventBeforeSaving() {
        CreateBuyDto dto = createDto(42L, 7L, List.of(10L));
        when(sectorByBatchRepository.findAllById(anyIterable()))
                .thenReturn(List.of(sectorByBatch(10L, 200, 99L)));

        assertThrows(InvalidBuyRequestException.class, () -> service.create(dto));
        verifyNoInteractions(buyRepository, ticketRepository);
    }

    @Test
    void createRejectsTotalThatDoesNotFitBuyIntegerColumn() {
        CreateBuyDto dto = createDto(42L, 7L, List.of(10L, 10L));
        when(sectorByBatchRepository.findAllById(anyIterable()))
                .thenReturn(List.of(sectorByBatch(10L, Integer.MAX_VALUE, 42L)));

        assertThrows(InvalidBuyRequestException.class, () -> service.create(dto));
        verifyNoInteractions(buyRepository, ticketRepository);
    }

    private CreateBuyDto createDto(long eventId, long userId, List<Long> ids) {
        CreateBuyDto dto = new CreateBuyDto();
        dto.setEventId(eventId);
        dto.setUserId(userId);
        dto.setSectorByBatchIds(new ArrayList<>(ids));
        return dto;
    }

    private SectorByBatch sectorByBatch(long id, int price, long eventId) {
        SimpleEvent event = new SimpleEvent();
        event.setId(eventId);

        Sector sector = new Sector();
        sector.setEvent(event);

        Batch batch = new Batch();
        batch.setEvent(event);

        SectorByBatch sectorByBatch = new SectorByBatch();
        sectorByBatch.setId(id);
        sectorByBatch.setPrice(price);
        sectorByBatch.setSector(sector);
        sectorByBatch.setBatch(batch);
        return sectorByBatch;
    }
}
