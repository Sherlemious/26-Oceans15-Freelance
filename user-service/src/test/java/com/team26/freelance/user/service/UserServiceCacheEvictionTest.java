package com.team26.freelance.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.team26.freelance.user.adapter.ObjectArrayDtoAdapter;
import com.team26.freelance.user.model.ProficiencyLevel;
import com.team26.freelance.user.model.Role;
import com.team26.freelance.user.model.Status;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.observer.AuthEventSubject;
import com.team26.freelance.user.repository.UserRepository;
import com.team26.freelance.user.repository.UserSkillRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceCacheEvictionTest {

    private final RepositoryState repositoryState = new RepositoryState();
    private final RecordingCacheEvictionService cacheEvictionService = new RecordingCacheEvictionService();
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository(repositoryState),
                userSkillRepository(repositoryState),
                new AuthEventSubject(List.of()),
                new ObjectArrayDtoAdapter(),
                cacheEvictionService
        );
        ReflectionTestUtils.setField(userService, "encoder", new TestPasswordEncoder());
    }

    @Test
    void createEvictsUserMutationCaches() {
        User user = user(56L);
        user.setPassword("raw");

        userService.create(user);

        assertEquals(56L, cacheEvictionService.lastUserMutationId);
    }

    @Test
    void updateEvictsUserMutationCaches() {
        repositoryState.user = user(56L);
        User updated = user(null);
        updated.setPassword("raw");

        userService.update(56L, updated);

        assertEquals(56L, cacheEvictionService.lastUserMutationId);
    }

    @Test
    void deleteEvictsUserMutationCaches() {
        repositoryState.user = user(56L);

        userService.delete(56L);

        assertEquals(56L, cacheEvictionService.lastUserMutationId);
    }

    @Test
    void updatePreferencesEvictsUserMutationCaches() {
        repositoryState.user = user(56L);
        repositoryState.user.setPreferences(new HashMap<>(Map.of("language", "en")));

        userService.updatePreferences(56L, Map.of("timezone", "UTC"));

        assertEquals(56L, cacheEvictionService.lastUserMutationId);
    }

    @Test
    void updateRoleEvictsUserMutationCaches() {
        repositoryState.user = user(56L);

        userService.updateRole(56L, "CLIENT");

        assertEquals(56L, cacheEvictionService.lastUserMutationId);
    }

    @Test
    void deactivateEvictsUserMutationCaches() {
        repositoryState.user = user(56L);
        repositoryState.activeContractCount = 0L;

        userService.deactivate(56L);

        assertEquals(56L, cacheEvictionService.lastUserMutationId);
    }

    @Test
    void setPrimarySkillEvictsUserMutationCaches() {
        User user = user(56L);
        UserSkill skill = skill(9L, user);
        user.setUserSkills(new ArrayList<>());
        user.getUserSkills().add(skill);
        repositoryState.user = user;
        repositoryState.skill = skill;

        userService.setPrimarySkill(56L, 9L);

        assertEquals(56L, cacheEvictionService.lastUserMutationId);
    }

    static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("Ann");
        user.setEmail("ann@example.com");
        user.setPassword("encoded");
        user.setPhone("555-0101");
        user.setRole(Role.FREELANCER);
        user.setStatus(Status.ACTIVE);
        user.setPreferences(new HashMap<>());
        return user;
    }

    static UserSkill skill(Long id, User user) {
        UserSkill skill = new UserSkill();
        skill.setId(id);
        skill.setSkillName("Java");
        skill.setCategory("Backend");
        skill.setYearsOfExperience(4);
        skill.setProficiencyLevel(ProficiencyLevel.EXPERT);
        skill.setIsPrimary(false);
        skill.setMetadata(new HashMap<>());
        skill.setUser(user);
        return skill;
    }

    static UserRepository userRepository(RepositoryState state) {
        return proxy(UserRepository.class, new UserRepositoryHandler(state));
    }

    static UserSkillRepository userSkillRepository(RepositoryState state) {
        return proxy(UserSkillRepository.class, new UserSkillRepositoryHandler(state));
    }

    private static <T> T proxy(Class<T> repositoryType, InvocationHandler handler) {
        return repositoryType.cast(Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                handler
        ));
    }

    static class RepositoryState {
        User user;
        UserSkill skill;
        long activeContractCount;
    }

    static class UserRepositoryHandler implements InvocationHandler {
        private final RepositoryState state;

        UserRepositoryHandler(RepositoryState state) {
            this.state = state;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findById" -> Optional.ofNullable(state.user);
                case "save" -> {
                    state.user = (User) args[0];
                    yield state.user;
                }
                case "delete", "withdrawSubmittedProposals" -> null;
                case "countActiveContracts" -> state.activeContractCount;
                case "toString" -> "UserRepositoryProxy";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    static class UserSkillRepositoryHandler implements InvocationHandler {
        private final RepositoryState state;

        UserSkillRepositoryHandler(RepositoryState state) {
            this.state = state;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findById" -> Optional.ofNullable(state.skill);
                case "save" -> {
                    state.skill = (UserSkill) args[0];
                    yield state.skill;
                }
                case "delete" -> null;
                case "toString" -> "UserSkillRepositoryProxy";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    static class RecordingCacheEvictionService extends UserCacheEvictionService {
        private Long lastUserMutationId;

        RecordingCacheEvictionService() {
            super(null);
        }

        @Override
        public void evictUserMutationCaches(Long userId) {
            this.lastUserMutationId = userId;
        }
    }

    static class TestPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return false;
        }
    }
}
