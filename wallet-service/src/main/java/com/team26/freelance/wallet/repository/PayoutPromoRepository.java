package com.team26.freelance.wallet.repository;

import com.team26.freelance.wallet.model.PayoutPromo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutPromoRepository extends JpaRepository<PayoutPromo, Long> {

    boolean existsByPayout_IdAndPromoCode_Id(Long payoutId, Long promoCodeId);
}
