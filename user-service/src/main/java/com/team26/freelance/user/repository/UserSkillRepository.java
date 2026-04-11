package com.team26.freelance.user.repository;

import com.team26.freelance.user.model.UserSkill;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {
    List<UserSkill> findByUserId(Long userId);
}
