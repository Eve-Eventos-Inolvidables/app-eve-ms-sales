package com.example.appevemssales.Service.Buy;

import com.example.appevecommon.Models.Buy.Buy;
import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevecommon.Models.Event.Batch.SectorByBatch;
import com.example.appevecommon.Models.Event.SimpleEvent;
import com.example.appevecommon.Models.User.User;
import com.example.appevecommon.Service.AbstractBaseService;
import com.example.appevecommon.Service.Exception.ResourceNotFoundException;
import com.example.appevemssales.Repository.IBuyRepository;
import com.example.appevemssales.Repository.ISectorByBatchRepository;
import com.example.appevemssales.Repository.ITicketRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import org.openapitools.model.BuyDto;
import org.openapitools.model.CreateBuyDto;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@Service
public class BuyService extends AbstractBaseService<Buy, BuyDto, BuyMapper, BuyFilter> {

    private final IBuyRepository buyRepository;
    private final ITicketRepository ticketRepository;
    private final ISectorByBatchRepository sectorByBatchRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public BuyService(
            IBuyRepository buyRepository,
            ITicketRepository ticketRepository,
            ISectorByBatchRepository sectorByBatchRepository,
            BuyMapper mapper) {
        super(buyRepository, mapper);
        this.buyRepository = buyRepository;
        this.ticketRepository = ticketRepository;
        this.sectorByBatchRepository = sectorByBatchRepository;
    }

    @Transactional
    public BuyDto create(CreateBuyDto dto) {
        if (dto == null) {
            throw new InvalidBuyRequestException("El cuerpo de la compra es obligatorio.");
        }
        if (dto.getEventId() == null || dto.getEventId() <= 0) {
            throw new InvalidBuyRequestException("eventId debe ser un entero positivo.");
        }
        if (dto.getUserId() == null || dto.getUserId() <= 0) {
            throw new InvalidBuyRequestException("userId debe ser un entero positivo.");
        }

        List<Long> selectedIds = dto.getSectorByBatchIds();
        if (selectedIds == null || selectedIds.isEmpty()) {
            throw new InvalidBuyRequestException(
                    "Debe seleccionar al menos un sector y tanda para comprar.");
        }

        Map<Long, Long> requestedCounts = countSelections(selectedIds);
        Map<Long, SectorByBatch> sectorByBatchById =
                loadAndValidateSectorByBatches(requestedCounts, dto.getEventId());
        int total = calculateTotal(requestedCounts, sectorByBatchById);

        SimpleEvent event = entityManager.find(SimpleEvent.class, dto.getEventId());
        if (event == null) {
            throw new ResourceNotFoundException(dto.getEventId());
        }
        User user = entityManager.find(User.class, dto.getUserId());
        if (user == null) {
            throw new ResourceNotFoundException(dto.getUserId());
        }

        Buy buy = mapper.toEntity(dto);
        buy.setEvent(event);
        buy.setUser(user);
        buy.setTotal(total);
        buy.setDate(LocalTime.now());

        Buy savedBuy = buyRepository.save(buy);

        List<Ticket> tickets = new ArrayList<>(selectedIds.size());
        for (Long sectorByBatchId : selectedIds) {
            Ticket ticket = new Ticket();
            ticket.setBuy(savedBuy);
            ticket.setSectorByBatch(sectorByBatchById.get(sectorByBatchId));
            ticket.setQrToken(UUID.randomUUID().toString());
            tickets.add(ticket);
        }
        ticketRepository.saveAll(tickets);

        return mapper.toDto(savedBuy);
    }

    private Map<Long, Long> countSelections(List<Long> selectedIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Long id : selectedIds) {
            if (id == null || id <= 0) {
                throw new InvalidBuyRequestException(
                        "Cada sectorByBatchId debe ser un entero positivo.");
            }
            counts.merge(id, 1L, Math::addExact);
        }
        return counts;
    }

    private Map<Long, SectorByBatch> loadAndValidateSectorByBatches(
            Map<Long, Long> requestedCounts,
            Long eventId) {
        List<SectorByBatch> sectorByBatches =
                sectorByBatchRepository.findAllById(requestedCounts.keySet());
        Map<Long, SectorByBatch> sectorByBatchById = new HashMap<>();
        for (SectorByBatch sectorByBatch : sectorByBatches) {
            sectorByBatchById.put(sectorByBatch.getId(), sectorByBatch);
        }

        for (Long requestedId : requestedCounts.keySet()) {
            SectorByBatch sectorByBatch = sectorByBatchById.get(requestedId);
            if (sectorByBatch == null) {
                // findAllById excludes archived rows through BaseEntity's SQL restriction.
                throw new ResourceNotFoundException(requestedId);
            }
            if (sectorByBatch.getPrice() < 1) {
                throw new InvalidBuyRequestException(
                        "El precio del sector/tanda " + requestedId + " no es válido.");
            }
            if (sectorByBatch.getSector() == null
                    || sectorByBatch.getSector().getEvent() == null
                    || sectorByBatch.getBatch() == null
                    || sectorByBatch.getBatch().getEvent() == null) {
                throw new InvalidBuyRequestException(
                        "El sector/tanda " + requestedId + " no tiene un evento válido asociado.");
            }

            Long sectorEventId = sectorByBatch.getSector().getEvent().getId();
            Long batchEventId = sectorByBatch.getBatch().getEvent().getId();
            if (!eventId.equals(sectorEventId) || !eventId.equals(batchEventId)) {
                throw new InvalidBuyRequestException(
                        "El sector/tanda " + requestedId
                                + " no pertenece al evento solicitado.");
            }
        }
        return sectorByBatchById;
    }

    private int calculateTotal(
            Map<Long, Long> requestedCounts,
            Map<Long, SectorByBatch> sectorByBatchById) {
        long total = 0;
        try {
            for (Map.Entry<Long, Long> selection : requestedCounts.entrySet()) {
                long lineTotal = Math.multiplyExact(
                        (long) sectorByBatchById.get(selection.getKey()).getPrice(),
                        selection.getValue());
                total = Math.addExact(total, lineTotal);
            }
        } catch (ArithmeticException ex) {
            throw new InvalidBuyRequestException(
                    "El total de la compra excede el rango soportado.");
        }

        if (total > Integer.MAX_VALUE) {
            throw new InvalidBuyRequestException(
                    "El total de la compra excede el máximo soportado por el sistema.");
        }
        return (int) total;
    }

    @Override
    public Specification<Buy> toSpecification(BuyFilter filter) {
        return (root, query, criteriaBuilder) -> {
            if (filter == null) {
                return criteriaBuilder.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (filter.getEventId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("event").get("id"), filter.getEventId()));
            }
            if (filter.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("user").get("id"), filter.getUserId()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
