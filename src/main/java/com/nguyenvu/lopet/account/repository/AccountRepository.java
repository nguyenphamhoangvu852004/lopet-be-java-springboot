package com.nguyenvu.lopet.account.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.account.entity.Account;

public interface AccountRepository extends JpaRepository<Account, Integer> {

    /**
     * Bản nạp kèm quan hệ, tương ứng {@code findById} của AccountRepoImpl (relations: profile +
     * accountRoles.role). Hầu hết luồng nghiệp vụ dùng bản này.
     */
    @EntityGraph(attributePaths = {"profile", "accountRoles", "accountRoles.role"})
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findDetailById(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"profile", "accountRoles", "accountRoles.role"})
    @Query("select a from Account a where a.email = :email")
    Optional<Account> findDetailByEmail(@Param("email") String email);

    @EntityGraph(attributePaths = {"profile", "accountRoles", "accountRoles.role"})
    @Query("select a from Account a where a.username = :username")
    Optional<Account> findDetailByUsername(@Param("username") String username);

    @EntityGraph(attributePaths = {"profile", "accountRoles", "accountRoles.role"})
    @Query("select distinct a from Account a")
    List<Account> findAllDetail();

    boolean existsByEmail(String email);

    /**
     * Gợi ý kết bạn. Ba điều kiện lấy nguyên từ {@code AccountRepoImpl.getSuggest}: loại mọi tài
     * khoản đã có quan hệ friendship ở BẤT KỲ trạng thái nào, loại chính mình, loại tài khoản bị khoá.
     *
     * <p>{@code ORDER BY RAND()} được giữ nguyên — tính ngẫu nhiên là một phần hành vi của endpoint,
     * không phải chi tiết cài đặt.
     */
    @Query(value = """
            select * from accounts a
            where a.deletedAt is null
              and a.isBanned = 0
              and a.id <> :accountId
              and not exists (
                    select 1 from friend_ships f
                    where f.deletedAt is null
                      and ((f.sender_id = :accountId and f.receiver_id = a.id)
                        or (f.receiver_id = :accountId and f.sender_id = a.id))
              )
            order by rand()
            """, nativeQuery = true)
    List<Account> findSuggestions(@Param("accountId") Integer accountId);

    /**
     * Bản có giới hạn số lượng. Tách riêng vì bên TS {@code .limit(NaN)} rơi vào nhánh falsy của
     * TypeORM nên câu lệnh KHÔNG có LIMIT — tức là thiếu tham số {@code limit} thì trả về tất cả.
     */
    @Query(value = """
            select * from accounts a
            where a.deletedAt is null
              and a.isBanned = 0
              and a.id <> :accountId
              and not exists (
                    select 1 from friend_ships f
                    where f.deletedAt is null
                      and ((f.sender_id = :accountId and f.receiver_id = a.id)
                        or (f.receiver_id = :accountId and f.sender_id = a.id))
              )
            order by rand()
            limit :maxResults
            """, nativeQuery = true)
    List<Account> findSuggestions(@Param("accountId") Integer accountId, @Param("maxResults") int maxResults);
}
