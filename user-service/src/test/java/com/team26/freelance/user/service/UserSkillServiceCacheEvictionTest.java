package com.team26.freelance.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.observer.AuthEventSubject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserSkillServiceCacheEvictionTest {

    private final UserServiceCacheEvictionTest.RepositoryState repositoryState =
            new UserServiceCacheEvictionTest.RepositoryState();
    private final RecordingCacheEvictionService cacheEvictionService = new RecordingCacheEvictionService();
    private UserSkillService userSkillService;

    @BeforeEach
    void setUp() {
        userSkillService = new UserSkillService(
                UserServiceCacheEvictionTest.userSkillRepository(repositoryState),
                UserServiceCacheEvictionTest.userRepository(repositoryState),
                new AuthEventSubject(List.of()),
                cacheEvictionService
        );
    }

    @Test
    void createEvictsUserSkillMutationCaches() {
        User user = UserServiceCacheEvictionTest.user(56L);
        UserSkill skill = UserServiceCacheEvictionTest.skill(9L, user);
        repositoryState.user = user;

        userSkillService.create(56L, skill);

        assertEquals(9L, cacheEvictionService.lastSkillMutationId);
        assertEquals(56L, cacheEvictionService.lastSkillMutationUserId);
    }

    @Test
    void updateEvictsUserSkillMutationCaches() {
        User user = UserServiceCacheEvictionTest.user(56L);
        UserSkill existing = UserServiceCacheEvictionTest.skill(9L, user);
        UserSkill updated = UserServiceCacheEvictionTest.skill(null, user);
        updated.setSkillName("Redis");
        repositoryState.skill = existing;

        userSkillService.update(9L, updated);

        assertEquals(9L, cacheEvictionService.lastSkillMutationId);
        assertEquals(56L, cacheEvictionService.lastSkillMutationUserId);
    }

    @Test
    void deleteEvictsUserSkillMutationCaches() {
        User user = UserServiceCacheEvictionTest.user(56L);
        UserSkill skill = UserServiceCacheEvictionTest.skill(9L, user);
        repositoryState.skill = skill;

        userSkillService.delete(9L);

        assertEquals(9L, cacheEvictionService.lastSkillMutationId);
        assertEquals(56L, cacheEvictionService.lastSkillMutationUserId);
    }

    @Test
    void deleteByUserSkillEvictsUserSkillMutationCaches() {
        User user = UserServiceCacheEvictionTest.user(56L);
        UserSkill skill = UserServiceCacheEvictionTest.skill(9L, user);
        repositoryState.user = user;
        repositoryState.skill = skill;

        userSkillService.deleteByUserSkill(56L, 9L);

        assertEquals(9L, cacheEvictionService.lastSkillMutationId);
        assertEquals(56L, cacheEvictionService.lastSkillMutationUserId);
    }

    static class RecordingCacheEvictionService extends UserCacheEvictionService {
        private Long lastSkillMutationId;
        private Long lastSkillMutationUserId;

        RecordingCacheEvictionService() {
            super(null);
        }

        @Override
        public void evictUserSkillMutationCaches(Long skillId, Long userId) {
            this.lastSkillMutationId = skillId;
            this.lastSkillMutationUserId = userId;
        }
    }
}
