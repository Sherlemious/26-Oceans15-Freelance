package com.team26.freelance.proposal.security;

import com.team26.freelance.proposal.repository.ProposalMilestoneRepository;
import com.team26.freelance.proposal.repository.ProposalRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("proposalAuthorization")
public class ProposalAuthorizationService {

    private final ProposalRepository proposalRepository;
    private final ProposalMilestoneRepository proposalMilestoneRepository;

    public ProposalAuthorizationService(ProposalRepository proposalRepository,
                                        ProposalMilestoneRepository proposalMilestoneRepository) {
        this.proposalRepository = proposalRepository;
        this.proposalMilestoneRepository = proposalMilestoneRepository;
    }

    public boolean canViewProposal(Long proposalId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        Long uid = getUid(authentication);
        if (uid == null) {
            return false;
        }

        if (hasRole(authentication, "ROLE_FREELANCER")) {
            return proposalRepository.isProposalOwnedByFreelancer(proposalId, uid);
        }
        if (hasRole(authentication, "ROLE_CLIENT")) {
            return proposalRepository.isProposalOwnedByClient(proposalId, uid);
        }
        return false;
    }

    public boolean canModifyProposal(Long proposalId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        Long uid = getUid(authentication);
        if (uid == null) {
            return false;
        }
        return hasRole(authentication, "ROLE_FREELANCER")
                && proposalRepository.isProposalOwnedByFreelancer(proposalId, uid);
    }

    public boolean canAcceptProposal(Long proposalId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        Long uid = getUid(authentication);
        if (uid == null) {
            return false;
        }
        return hasRole(authentication, "ROLE_CLIENT")
                && proposalRepository.isProposalOwnedByClient(proposalId, uid);
    }

    public boolean canViewMilestone(Long milestoneId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        Long uid = getUid(authentication);
        if (uid == null) {
            return false;
        }

        if (hasRole(authentication, "ROLE_FREELANCER")) {
            return proposalMilestoneRepository.isMilestoneOwnedByFreelancer(milestoneId, uid);
        }
        if (hasRole(authentication, "ROLE_CLIENT")) {
            return proposalMilestoneRepository.isMilestoneRelatedToClient(milestoneId, uid);
        }
        return false;
    }

    public boolean canModifyMilestone(Long milestoneId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        Long uid = getUid(authentication);
        if (uid == null) {
            return false;
        }
        return hasRole(authentication, "ROLE_FREELANCER")
                && proposalMilestoneRepository.isMilestoneOwnedByFreelancer(milestoneId, uid);
    }

    public boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    /**
     * security-common sets:
     *  - principal = JWT subject (email)
     *  - credentials = JWT uid (string)
     */
    public Long getUid(Authentication authentication) {
        if (authentication == null || authentication.getCredentials() == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(authentication.getCredentials()));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a -> role.equalsIgnoreCase(a.getAuthority()));
    }
}
