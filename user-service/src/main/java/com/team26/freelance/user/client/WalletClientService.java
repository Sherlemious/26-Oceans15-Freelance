package com.team26.freelance.user.client;

import com.team26.freelance.user.exception.NotFoundException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class WalletClientService {

    private static final Logger log = LoggerFactory.getLogger(WalletClientService.class);

    private final WalletServiceClient walletServiceClient;

    public WalletClientService(WalletServiceClient walletServiceClient) {
        this.walletServiceClient = walletServiceClient;
    }

    public BigDecimal getFreelancerPayoutTotal(Long freelancerId, String startDate, String endDate) {
        try {
            log.info("Calling WalletServiceClient.getFreelancerPayoutTotal with args={}", freelancerId);
            BigDecimal result = walletServiceClient.getFreelancerPayoutTotal(freelancerId, startDate, endDate);
            log.info("WalletServiceClient.getFreelancerPayoutTotal returned successfully");
            return result;
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Freelancer not found in wallet-service: " + freelancerId);
        } catch (FeignException e) {
            log.warn("Feign call to wallet-service failed: {}", e.getMessage());
            throw e;
        }
    }
}
