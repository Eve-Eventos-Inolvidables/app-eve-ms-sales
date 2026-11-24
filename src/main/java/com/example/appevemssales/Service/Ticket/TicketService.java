package com.example.appevemssales.Service.Ticket;

import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevecommon.Service.Exception.ResourceNotFoundException;
import com.example.appevecommon.Service.AbstractBaseService;
import com.example.appevemssales.Repository.ITicketRepository;
import jakarta.persistence.criteria.Predicate;
import org.openapitools.model.TicketDto;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TicketService extends AbstractBaseService<Ticket, TicketDto, TicketMapper, TicketFilter> {

    private final ITicketRepository ticketRepository;

    public TicketService(ITicketRepository repository, TicketMapper mapper) {
        super(repository, mapper);
        this.ticketRepository = repository;
    }

    public TicketDto getByIdForUser(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findByIdAndBuy_User_Id(ticketId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ticketId));
        return mapper.toDto(ticket);
    }

    @Override
    public Specification<Ticket> toSpecification(TicketFilter filter) {
        return (root, query, criteriaBuilder) -> {
            if (filter == null) {
                return criteriaBuilder.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (filter.getBuyId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("buy").get("id"), filter.getBuyId()));
            }
            if (filter.getSectorByBatchId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("sectorByBatch").get("id"), filter.getSectorByBatchId()));
            }
            if (filter.getEventId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("buy").get("event").get("id"), filter.getEventId()));
            }
            if (filter.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("buy").get("user").get("id"), filter.getUserId()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
