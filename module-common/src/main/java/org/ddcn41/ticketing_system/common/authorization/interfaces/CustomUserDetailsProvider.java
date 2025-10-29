package org.ddcn41.ticketing_system.common.authorization.interfaces;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;


@Deprecated(forRemoval = true)
@ConditionalOnProperty(name = "use.legacy.auth", havingValue = "true")
public interface CustomUserDetailsProvider {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}
