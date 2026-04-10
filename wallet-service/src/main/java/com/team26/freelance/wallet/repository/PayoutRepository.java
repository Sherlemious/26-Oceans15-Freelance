package com.team26.freelance.wallet.repository;

import com.team26.freelance.wallet.model.Payout;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {

    @Query("""
            select distinct p
            from Payout p
            left join fetch p.payoutPromos pp
            left join fetch pp.promoCode
            where p.id = :id
            """)
    Optional<Payout> findByIdWithPromos(@Param("id") Long id);
}
