package com.example.appevemssales.Service.Buy;

import com.example.appevecommon.Models.Buy.Buy;
import com.example.appevecommon.Service.AbstractBaseService;
import com.example.appevecommon.Service.Exception.ResourceNotFoundException;
import com.example.appevemssales.Client.PriceGatewayClient;
import com.example.appevemssales.Repository.IBuyRepository;
import com.example.appevemssales.Repository.ITicketRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.model.BuyDto;
import org.openapitools.model.CreateBuyDto;
import org.openapitools.model.SectorByBatchDto;
import org.openapitools.model.SimpleEventDto;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BuyService extends AbstractBaseService<Buy, BuyDto, BuyMapper, BuyFilter> {

    private final IBuyRepository buyRepository;
    private final ITicketRepository ticketRepository;
    private final PriceGatewayClient priceGatewayClient;
    private final BuyPersistenceService persistenceService;

    public BuyService(
            IBuyRepository buyRepository,
            ITicketRepository ticketRepository,
            BuyMapper mapper,
            PriceGatewayClient priceGatewayClient,
            BuyPersistenceService persistenceService) {
        super(buyRepository, mapper);
        this.buyRepository = buyRepository;
        this.ticketRepository = ticketRepository;
        this.priceGatewayClient = priceGatewayClient;
        this.persistenceService = persistenceService;
    }

    public BuyDto create(
            CreateBuyDto dto,
            Long authenticatedUserId,
            String authorizationHeader) {
        if (dto == null) {
            throw new InvalidBuyRequestException("El cuerpo de la compra es obligatorio.");
        }
        if (authenticatedUserId == null || authenticatedUserId <= 0) {
            throw new InvalidAuthenticatedUserException();
        }
        if (dto.getEventId() == null || dto.getEventId() <= 0) {
            throw new InvalidBuyRequestException("eventId debe ser un entero positivo.");
        }

        List<Long> selectedIds = dto.getSectorByBatchIds();
        if (selectedIds == null || selectedIds.isEmpty()) {
            throw new InvalidBuyRequestException(
                    "Debe seleccionar al menos un sector y tanda para comprar.");
        }

        Map<Long, Long> requestedCounts = countSelections(selectedIds);
        SimpleEventDto event = priceGatewayClient.getEventById(
                dto.getEventId(), authorizationHeader);
        validateEventIsOpen(event);
        validatePerPersonLimit(dto.getEventId(), selectedIds.size(),
                event.getTicketsPerPersonLimit(), authenticatedUserId);

        List<SectorByBatchDto> consumedPrices = priceGatewayClient.consumeStock(
                dto.getEventId(), selectedIds, authorizationHeader);
        try {
            Map<Long, SectorByBatchDto> pricesById =
                    validatePriceResponse(requestedCounts, consumedPrices);
            int total = calculateTotal(requestedCounts, pricesById);
            return persistenceService.persist(dto, total, authenticatedUserId);
        } catch (RuntimeException purchaseFailure) {
            try {
                priceGatewayClient.releaseStock(
                        dto.getEventId(), selectedIds, authorizationHeader);
            } catch (RuntimeException compensationFailure) {
                log.error(
                        "No se pudo restaurar el stock para el evento {} después de fallar la compra del usuario {}",
                        dto.getEventId(),
                        authenticatedUserId,
                        compensationFailure);
                purchaseFailure.addSuppressed(compensationFailure);
            }
            throw purchaseFailure;
        }
    }

    public BuyDto getByIdForUser(Long buyId, Long userId) {
        Buy buy = buyRepository.findByIdAndUser_Id(buyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(buyId));
        return mapper.toDto(buy);
    }

    private void validateEventIsOpen(SimpleEventDto event) {
        if (event == null
                || Boolean.TRUE.equals(event.getArchived())
                || event.getDate() == null
                || event.getVisibleStart() == null) {
            throw new InvalidBuyRequestException("El evento no está habilitado para la compra.");
        }

        LocalDate today = LocalDate.now();
        if (event.getVisibleStart().isAfter(today) || event.getDate().isBefore(today)) {
            throw new InvalidBuyRequestException("El evento no está habilitado para la compra.");
        }
        if (event.getTicketsPerPersonLimit() == null
                || event.getTicketsPerPersonLimit() <= 0) {
            throw new InvalidBuyRequestException("El evento no tiene un límite de compra válido.");
        }
    }

    private void validatePerPersonLimit(
            Long eventId,
            int requestedCount,
            Integer limit,
            Long userId) {
        long alreadyPurchased =
                ticketRepository.countByBuy_User_IdAndBuy_Event_Id(userId, eventId);
        if (alreadyPurchased >= limit || requestedCount > limit - alreadyPurchased) {
            throw new PurchaseLimitExceededException(
                    "La cantidad solicitada supera el límite de entradas por persona.");
        }
    }

    private Map<Long, Long> countSelections(List<Long> selectedIds) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Long id : selectedIds) {
            if (id == null || id <= 0) {
                throw new InvalidBuyRequestException(
                        "Cada sectorByBatchId debe ser un entero positivo.");
            }
            counts.merge(id, 1L, Math::addExact);
        }
        return counts;
    }

    private Map<Long, SectorByBatchDto> validatePriceResponse(
            Map<Long, Long> requestedCounts,
            List<SectorByBatchDto> priceDtos) {
        if (priceDtos == null) {
            throw new PriceGatewayException(
                    502, "El servicio de precios devolvió una respuesta inválida.");
        }

        Map<Long, SectorByBatchDto> pricesById = new HashMap<>();
        for (SectorByBatchDto priceDto : priceDtos) {
            if (priceDto == null
                    || priceDto.getId() == null
                    || !requestedCounts.containsKey(priceDto.getId())) {
                throw new PriceGatewayException(
                        502, "El servicio de precios devolvió IDs inesperados.");
            }
            if (pricesById.putIfAbsent(priceDto.getId(), priceDto) != null) {
                throw new PriceGatewayException(
                        502, "El servicio de precios devolvió IDs duplicados.");
            }
            if (priceDto.getPrice() == null || priceDto.getPrice() < 1) {
                throw new PriceGatewayException(
                        502, "El servicio de precios devolvió un precio inválido.");
            }
        }

        for (Long requestedId : requestedCounts.keySet()) {
            if (!pricesById.containsKey(requestedId)) {
                throw new ResourceNotFoundException(requestedId);
            }
        }
        return pricesById;
    }

    private int calculateTotal(
            Map<Long, Long> requestedCounts,
            Map<Long, SectorByBatchDto> pricesById) {
        long total = 0;
        try {
            for (Map.Entry<Long, Long> selection : requestedCounts.entrySet()) {
                long lineTotal = Math.multiplyExact(
                        (long) pricesById.get(selection.getKey()).getPrice(),
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
