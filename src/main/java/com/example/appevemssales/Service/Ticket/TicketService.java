package com.example.appevemssales.Service.Ticket;

import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevecommon.Repository.IBaseRepository;
import com.example.appevecommon.Service.AbstractBaseService;
import jakarta.persistence.criteria.Predicate;
import org.openapitools.model.TicketDto;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TicketService extends AbstractBaseService<Ticket, TicketDto, TicketMapper, TicketFilter> {

    public TicketService(IBaseRepository<Ticket> repository, TicketMapper mapper) {
        super(repository, mapper);
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

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
