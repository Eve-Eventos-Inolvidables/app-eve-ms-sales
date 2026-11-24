package com.example.appevemssales.Repository;

import com.example.appevecommon.Models.Buy.Buy;
import com.example.appevecommon.Repository.IBaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IBuyRepository extends IBaseRepository<Buy> {

    Optional<Buy> findByIdAndUser_Id(Long id, Long userId);
}
