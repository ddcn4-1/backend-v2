package org.ddcn41.ticketing_system.common.authorization.interfaces;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface CustomUserDetailsProvider {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}
