package com.team26.freelance.contract.controller;

import com.team26.freelance.contract.dto.StalledContractDTO;
import com.team26.freelance.contract.service.StalledContractService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class StalledContractController {

    private final StalledContractService stalledContractService;

    public StalledContractController(StalledContractService stalledContractService) {
        this.stalledContractService = stalledContractService;
    }

    @GetMapping("/stalled")
    public List<StalledContractDTO> getStalledContracts(
            @RequestParam("maxProgress") double maxProgress,
            @RequestParam("stalledDays") double stalledDays) {

        return stalledContractService.getStalledContracts(maxProgress, stalledDays);
    }
}

