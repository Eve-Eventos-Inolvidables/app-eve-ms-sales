package com.example.appevemssales.Repository;

import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevecommon.Repository.IBaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ITicketRepository extends IBaseRepository<Ticket> {

    Optional<Ticket> findByIdAndBuy_User_Id(Long id, Long userId);

    long countByBuy_User_IdAndBuy_Event_Id(Long userId, Long eventId);

    @Query("""
            select ticket.sectorByBatch.id, count(ticket)
            from Tickets ticket
            where ticket.sectorByBatch.id in :sectorByBatchIds
            group by ticket.sectorByBatch.id
            """)
    List<Object[]> countSoldTicketsBySectorByBatchIds(
            @Param("sectorByBatchIds") Collection<Long> sectorByBatchIds);
}
