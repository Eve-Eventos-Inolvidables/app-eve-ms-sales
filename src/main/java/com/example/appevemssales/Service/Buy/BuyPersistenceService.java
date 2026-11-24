package com.example.appevemssales.Service.Buy;

import com.example.appevecommon.Models.Buy.Buy;
import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevecommon.Models.Event.Batch.SectorByBatch;
import com.example.appevecommon.Models.User.User;
import com.example.appevemssales.Repository.IBuyRepository;
import com.example.appevemssales.Repository.ITicketRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.openapitools.model.BuyDto;
import org.openapitools.model.CreateBuyDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.UUID;

@Service
public class BuyPersistenceService {

    private final IBuyRepository buyRepository;
    private final ITicketRepository ticketRepository;
    private final BuyMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public BuyPersistenceService(
            IBuyRepository buyRepository,
            ITicketRepository ticketRepository,
            BuyMapper mapper) {
        this.buyRepository = buyRepository;
        this.ticketRepository = ticketRepository;
        this.mapper = mapper;
    }

    @Transactional
    public BuyDto persist(CreateBuyDto dto, int total, Long authenticatedUserId) {
        Buy buy = mapper.toEntity(dto);
        buy.setUser(entityManager.getReference(User.class, authenticatedUserId));
        buy.setTotal(total);
        buy.setDate(LocalTime.now());

        Buy savedBuy = buyRepository.save(buy);

        var selectedIds = dto.getSectorByBatchIds();
        var tickets = new ArrayList<Ticket>(selectedIds.size());
        for (Long sectorByBatchId : selectedIds) {
            Ticket ticket = new Ticket();
            ticket.setBuy(savedBuy);
            // Events has already resolved and validated this ID. Use a reference only:
            // Sales must not query the Events-owned price table.
            ticket.setSectorByBatch(
                    entityManager.getReference(SectorByBatch.class, sectorByBatchId));
            ticket.setQrToken(UUID.randomUUID().toString());
            tickets.add(ticket);
        }
        ticketRepository.saveAll(tickets);

        return mapper.toDto(savedBuy);
    }
}
