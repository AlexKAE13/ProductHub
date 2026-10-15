package ru.eltex.producthub.security;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HubUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public HubUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public HubUserDetails loadUserByUsername(String login) {
        return users.findByLoginAndActiveTrue(login)
                .map(HubUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + login));
    }

    @Transactional(readOnly = true)
    public List<HubUserDetails> activeUsers() {
        return users.findAllByActiveTrueOrderByIdAsc().stream().map(HubUserDetails::new).toList();
    }
}
