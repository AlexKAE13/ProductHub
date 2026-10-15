package ru.eltex.producthub.security;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginAndActiveTrue(String login);

    Optional<User> findByLogin(String login);

    List<User> findAllByActiveTrueOrderByIdAsc();

    @Query("select u from User u where :role member of u.roles and u.active = true order by u.fullName")
    List<User> findActiveByRole(@Param("role") Role role);
}
