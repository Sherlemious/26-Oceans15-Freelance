package com.team26.freelance.user.service;

import com.team26.freelance.user.dto.UserDTO;
import com.team26.freelance.user.dto.UserContractSummaryDTO;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserRole;
import com.team26.freelance.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Search for users based on name, email, and role filters.
     * All filters are optional (can be null). Matching is case-insensitive.
     * Name and email support partial matching.
     * 
     * @param name Name filter (partial match, case-insensitive)
     * @param email Email filter (partial match, case-insensitive)
     * @param role Role filter (exact match)
     * @return List of users matching the filters, or empty list if no matches
     */
    public List<UserDTO> searchUsers(String name, String email, UserRole role) {
        List<User> users = userRepository.searchUsers(name, email, role);
        return users.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Create a new user
     */
    public UserDTO createUser(String name, String email, UserRole role) {
        User user = new User(name, email, role);
        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }
    
    /**
     * Create a new user with additional details
     */
    public UserDTO createUser(String name, String email, UserRole role, String phone, String bio) {
        User user = new User(name, email, role, phone, bio);
        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }
    
    /**
     * Get user by ID
     */
    public Optional<UserDTO> getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::convertToDTO);
    }
    
    /**
     * Get all users
     */
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Update an existing user
     */
    public UserDTO updateUser(Long id, String name, String email, UserRole role, String phone, String bio) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (name != null) user.setName(name);
            if (email != null) user.setEmail(email);
            if (role != null) user.setRole(role);
            if (phone != null) user.setPhone(phone);
            if (bio != null) user.setBio(bio);
            User updatedUser = userRepository.save(user);
            return convertToDTO(updatedUser);
        }
        return null;
    }
    
    /**
     * Delete a user by ID
     */
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }
    
    /**
     * S1-F2: Update user preferences (JSONB)
     * Merges incoming preferences into existing preferences.
     * Overwrites existing keys, adds new ones.
     * Throws exception if user not found (returns null which controller converts to 404).
     */
    public UserDTO updatePreferences(Long id, JsonNode incomingPreferences) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            
            // Get existing preferences or create new ObjectNode if null
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode mergedPreferences;
            
            if (user.getPreferences() != null && user.getPreferences().isObject()) {
                mergedPreferences = (ObjectNode) user.getPreferences();
            } else {
                mergedPreferences = mapper.createObjectNode();
            }
            
            // Merge incoming preferences
            if (incomingPreferences != null && incomingPreferences.isObject()) {
                incomingPreferences.fields().forEachRemaining(entry -> 
                    mergedPreferences.set(entry.getKey(), entry.getValue())
                );
            }
            
            user.setPreferences(mergedPreferences);
            User updatedUser = userRepository.save(user);
            return convertToDTO(updatedUser);
        }
        return null;
    }

    /**
     * S1-F3: Get aggregated contract summary for a user.
     * Returns null when user is not found (controller maps to 404).
     */
    public UserContractSummaryDTO getUserContractSummary(Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return null;
        }

        User user = userOptional.get();

        if (!tableExists("contracts")) {
            return emptyContractSummary(user);
        }

        Set<String> contractColumns = getTableColumns("contracts");
        String joinCondition = buildJoinCondition(contractColumns);
        if (joinCondition == null) {
            return emptyContractSummary(user);
        }

        String statusExpression = buildStatusExpression(contractColumns);
        String amountExpression = buildAmountExpression(contractColumns);

        String sql = "SELECT u.id AS user_id, u.name AS name, " +
                "COUNT(c.id) AS total_contracts, " +
                "COALESCE(SUM(CASE WHEN " + statusExpression + " = 'completed' THEN 1 ELSE 0 END), 0) AS completed_contracts, " +
                "COALESCE(SUM(CASE WHEN " + statusExpression + " = 'terminated' THEN 1 ELSE 0 END), 0) AS terminated_contracts, " +
                "COALESCE(SUM(CASE WHEN " + statusExpression + " = 'completed' THEN " + amountExpression + " ELSE 0 END), 0) AS total_earnings, " +
                "COALESCE(AVG(CASE WHEN " + statusExpression + " = 'completed' THEN " + amountExpression + " ELSE NULL END), 0) AS average_contract_value " +
                "FROM users u " +
                "LEFT JOIN contracts c ON " + joinCondition + " " +
                "WHERE u.id = ? " +
                "GROUP BY u.id, u.name";

        Map<String, Object> row = jdbcTemplate.queryForMap(sql, userId);

        return new UserContractSummaryDTO(
                toLong(row.get("user_id")),
                String.valueOf(row.get("name")),
                toLong(row.get("total_contracts")),
                toLong(row.get("completed_contracts")),
                toLong(row.get("terminated_contracts")),
                toBigDecimal(row.get("total_earnings")),
                toBigDecimal(row.get("average_contract_value"))
        );
    }

    private UserContractSummaryDTO emptyContractSummary(User user) {
        return new UserContractSummaryDTO(
                user.getId(),
                user.getName(),
                0L,
                0L,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private boolean tableExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                Integer.class,
                tableName
        );
        return tableCount != null && tableCount > 0;
    }

    private Set<String> getTableColumns(String tableName) {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class,
                tableName
        );

        return columns.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private String buildJoinCondition(Set<String> contractColumns) {
        List<String> conditions = new ArrayList<>();

        if (contractColumns.contains("user_id")) {
            conditions.add("c.user_id = u.id");
        }
        if (contractColumns.contains("freelancer_id")) {
            conditions.add("c.freelancer_id = u.id");
        }
        if (contractColumns.contains("client_id")) {
            conditions.add("c.client_id = u.id");
        }
        if (contractColumns.contains("userid")) {
            conditions.add("c.userid = u.id");
        }
        if (contractColumns.contains("freelancerid")) {
            conditions.add("c.freelancerid = u.id");
        }
        if (contractColumns.contains("clientid")) {
            conditions.add("c.clientid = u.id");
        }

        if (conditions.isEmpty()) {
            return null;
        }

        return String.join(" OR ", conditions);
    }

    private String buildStatusExpression(Set<String> contractColumns) {
        if (contractColumns.contains("status")) {
            return "LOWER(CAST(c.status AS text))";
        }
        if (contractColumns.contains("contract_status")) {
            return "LOWER(CAST(c.contract_status AS text))";
        }
        if (contractColumns.contains("contractstatus")) {
            return "LOWER(CAST(c.contractstatus AS text))";
        }

        return "''";
    }

    private String buildAmountExpression(Set<String> contractColumns) {
        if (contractColumns.contains("agreed_amount")) {
            return "COALESCE(c.agreed_amount, 0)";
        }
        if (contractColumns.contains("agreed_amounts")) {
            return "COALESCE(c.agreed_amounts, 0)";
        }
        if (contractColumns.contains("agreedamount")) {
            return "COALESCE(c.agreedamount, 0)";
        }
        if (contractColumns.contains("agreedamounts")) {
            return "COALESCE(c.agreedamounts, 0)";
        }
        if (contractColumns.contains("amount")) {
            return "COALESCE(c.amount, 0)";
        }
        if (contractColumns.contains("contract_value")) {
            return "COALESCE(c.contract_value, 0)";
        }
        if (contractColumns.contains("contractvalue")) {
            return "COALESCE(c.contractvalue, 0)";
        }

        return "0";
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }
    
    /**
     * Convert User entity to UserDTO
     */
    private UserDTO convertToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getPhone(),
                user.getBio(),
                user.getPreferences()
        );
    }
}
