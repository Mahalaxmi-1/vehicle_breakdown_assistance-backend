package com.roadassist.backend.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.roadassist.backend.repository.UserRepository;

@Service
public class RoadAssistUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public RoadAssistUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        var account = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        return User.withUsername(account.getEmail())
            .password(account.getPassword())
            .roles(account.getRole().name())
            .disabled(!account.isActive())
            .build();
    }
}
