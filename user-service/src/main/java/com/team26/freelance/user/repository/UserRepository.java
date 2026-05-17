package com.team26.freelance.user.repository;

import com.team26.freelance.user.model.Role;
import com.team26.freelance.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<User> findByEmail(String email);

    @Query("""
            SELECT u FROM User u
            WHERE (:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
              AND (:role IS NULL OR u.role = :role)
            """)
    List<User> searchUsers(
            @Param("name") String name,
            @Param("email") String email,
            @Param("role") Role role);

    @Query(value = "SELECT * FROM users WHERE preferences @> CAST(:pref AS jsonb)",
            nativeQuery = true)
    List<User> findByPreference(@Param("pref") String prefJson);

    @Query(value = """
            SELECT * FROM users
            WHERE LOWER(TRIM(BOTH FROM COALESCE(preferences->>'language', ''))) = LOWER(:language)
            """, nativeQuery = true)
    List<User> findByPreferredLanguage(@Param("language") String language);
}
