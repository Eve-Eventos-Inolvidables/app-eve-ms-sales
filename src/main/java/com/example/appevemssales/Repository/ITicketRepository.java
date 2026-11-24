package com.example.appevemssales.Repository;

import com.example.appevecommon.Models.Buy.Ticket;
import com.example.appevecommon.Repository.IBaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ITicketRepository extends IBaseRepository<Ticket> {

    Optional<Ticket> findByIdAndBuy_User_Id(Long id, Long userId);

    long countByBuy_User_IdAndBuy_Event_Id(Long userId, Long eventId);
}
