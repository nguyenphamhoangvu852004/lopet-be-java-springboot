package com.nguyenvu.lopet.account.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.account.entity.Account;

public interface AccountRepository extends JpaRepository<Account, Integer> {

    @EntityGraph(attributePaths = {"accountProfile", "accountRoles", "accountRoles.role"})
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findDetailById(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"accountProfile", "accountRoles", "accountRoles.role"})
    @Query("select a from Account a where a.email = :email")
    Optional<Account> findDetailByEmail(@Param("email") String email);

    @EntityGraph(attributePaths = {"accountProfile", "accountRoles", "accountRoles.role"})
    @Query("select a from Account a where a.username = :username")
    Optional<Account> findDetailByUsername(@Param("username") String username);

    @EntityGraph(attributePaths = {"accountProfile", "accountRoles", "accountRoles.role"})
    @Query("select distinct a from Account a")
    List<Account> findAllDetail();

    boolean existsByEmail(String email);

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
