package com.team26.freelance.proposal.controller;

import com.team26.freelance.proposal.dto.CreateProposalDTO;
import com.team26.freelance.proposal.dto.UpdateProposalDTO;
import com.team26.freelance.proposal.dto.FeeEstimateDTO;
import com.team26.freelance.proposal.dto.FeeEstimateRequest;
import com.team26.freelance.proposal.dto.ProposalDetailsDTO;
import com.team26.freelance.proposal.dto.ProposalAnalyticsDTO;
import com.team26.freelance.proposal.dto.ProposalAnalyticsDashboardDTO;
import com.team26.freelance.proposal.dto.JobRecommendationDTO;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.model.ProposalMilestone;
import com.team26.freelance.proposal.service.ProposalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    // ── Proposal CRUD ──────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<Proposal>> getAllProposals() {
        return ResponseEntity.ok(proposalService.getAllProposals());
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN') and @proposalAuthorization.canViewProposal(#id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<Proposal> getProposalById(@NonNull @PathVariable Long id) {
        return ResponseEntity.ok(proposalService.getProposalById(id));
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN') and (@proposalAuthorization.isAdmin(authentication) or @proposalAuthorization.getUid(authentication) == #request.freelancerId())")
    @PostMapping
    public ResponseEntity<Proposal> createProposal(@Valid @RequestBody CreateProposalDTO request) {
        return ResponseEntity.status(201).body(proposalService.createProposal(request));
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN') and @proposalAuthorization.canModifyProposal(#id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<Proposal> updateProposal(@NonNull @PathVariable Long id,
                                                   @Valid @RequestBody UpdateProposalDTO proposal) {
        return ResponseEntity.ok(proposalService.updateProposal(id, proposal));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProposal(@NonNull @PathVariable Long id) {
        proposalService.deleteProposal(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<List<Proposal>> searchProposals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(proposalService.searchByStatusAndDateRange(status, startDate, endDate));
    }

    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN') and @proposalAuthorization.canAcceptProposal(#proposalId, authentication)")
    @PutMapping("/{proposalId}/accept")
    public ResponseEntity<Proposal> acceptProposal(@NonNull @PathVariable Long proposalId) {
        return ResponseEntity.ok(proposalService.acceptProposal(proposalId));
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN')")
    @PostMapping("/estimate")
    public ResponseEntity<FeeEstimateDTO> estimateFee(@Valid @RequestBody FeeEstimateRequest request) {
        return ResponseEntity.ok(proposalService.estimateFee(
                request.getBidAmount(), request.getEstimatedDays()));
    }

    // ── S3-F4: Complete Proposal's Contract ─────────────────────────────────

    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN') and @proposalAuthorization.canModifyProposal(#id, authentication)")
    @PutMapping("/{id}/complete")
    public ResponseEntity<Proposal> completeProposalContract(@NonNull @PathVariable Long id) {
        Proposal completedProposal = proposalService.completeProposalContract(id);
        return ResponseEntity.ok(completedProposal);
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN') and @proposalAuthorization.canModifyProposal(#id, authentication)")
    @PutMapping("/{id}/withdraw")
    public ResponseEntity<Proposal> withdrawProposal(@NonNull @PathVariable Long id) {
        Proposal withdrawnProposal = proposalService.withdrawProposal(id);
        return ResponseEntity.ok(withdrawnProposal);
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN') and @proposalAuthorization.canModifyProposal(#proposalId, authentication)")
    @PostMapping("/{proposalId}/milestones")
    public ResponseEntity<Proposal> addMilestonesToProposal(@NonNull @PathVariable Long proposalId,
            @RequestBody List<ProposalMilestone> milestones) {
        Proposal updatedProposal = proposalService.addMilestoneToProposal(proposalId, milestones);
        return ResponseEntity.ok(updatedProposal);
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN') and @proposalAuthorization.canViewProposal(#proposalId, authentication)")
    @GetMapping("/{proposalId}/details")
    public ResponseEntity<ProposalDetailsDTO> getProposalDetails(@NonNull @PathVariable Long proposalId) {
        return ResponseEntity.ok(proposalService.getProposalDetails(proposalId));
    }

    // ── S3-F5: Filter Proposals by Metadata ─────────────────────────────────

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN')")
    @GetMapping("/metadata/search")
    public ResponseEntity<List<Proposal>> searchByMetadata(
            @NotBlank @RequestParam(required = true) String key,
            @NotBlank @RequestParam(required = true) String value) {
        List<Proposal> results = proposalService.filterProposalsByMetadata(key, value);
        return ResponseEntity.ok(results);
    }

    // ── S3-F6: Proposal Analytics by Time Period ────────────────────────────

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN')")
    @GetMapping("/analytics")
    public ResponseEntity<ProposalAnalyticsDTO> getAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        // Convert to timestamp: start at 00:00:00, end at 23:59:59
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        ProposalAnalyticsDTO report = proposalService.getProposalAnalytics(start, end);
        return ResponseEntity.ok(report);
    }

    // ── S3-F10: Proposal Analytics Dashboard ───────────────────────────────

    @PreAuthorize("hasAnyRole('FREELANCER', 'CLIENT', 'ADMIN')")
    @GetMapping("/analytics/dashboard")
    public ResponseEntity<ProposalAnalyticsDashboardDTO> getAnalyticsDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(
                proposalService.getProposalAnalyticsDashboard(startDate, endDate));
    }

    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN') and @proposalAuthorization.canModifyProposal(#proposalId, authentication)")
    @PostMapping("/{proposalId}/record-interaction")
    public ResponseEntity<String> recordInteraction(@PathVariable Long proposalId) {
        String result = proposalService.recordInteraction(proposalId);
        return ResponseEntity.ok(result);
    }

    // ── S3-F12: Recommendations ──────────────────────────────────────────

    @PreAuthorize("hasAnyRole('FREELANCER', 'ADMIN')")
    @GetMapping("/recommendations")
    public ResponseEntity<List<JobRecommendationDTO>> getRecommendations(
            @RequestParam Long freelancerId,
            @RequestParam(required = false) Integer limit,
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        int effectiveLimit = (limit == null) ? 5 : limit;
        if (effectiveLimit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be a positive integer");
        }

        Long callerUid;
        try {
            callerUid = Long.valueOf(String.valueOf(authentication.getCredentials()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid uid claim");
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));

        if (!isAdmin && !callerUid.equals(freelancerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ownership violation");
        }

        return ResponseEntity.ok(
                proposalService.getRecommendedJobsForFreelancer(freelancerId, effectiveLimit)
        );
    }


}