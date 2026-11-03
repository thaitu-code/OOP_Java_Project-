package vn.id.fitnessai.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.id.fitnessai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Chỉ dữ liệu cần cho quyền thao tác workout; không tải roles/profile ở mỗi heartbeat.
    interface WorkoutAccess {
        Long getId();
        boolean getEnabled();
        boolean getLocked();
    }

    @Query("select u.id as id," +
            " u.isEnabled as enabled, " +
            "u.isLocked as locked " +
            "from User u where u.username = :username")
    Optional<WorkoutAccess> findWorkoutAccessByUsername(@Param("username") String username);

    // Vẫn khóa cùng dòng User trước Workout; projection tránh hydrate toàn bộ tài khoản.
    @Query(value = "select id, is_enabled as enabled, is_locked as locked from users where username = :username for update", nativeQuery = true)
    Optional<WorkoutAccess> findWorkoutAccessForUpdate(@Param("username") String username);

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username =:username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE u.isEnabled = true AND u.isLocked = false AND u.email IS NOT NULL")
    List<User> findAllActiveUsersForReminder();
}
