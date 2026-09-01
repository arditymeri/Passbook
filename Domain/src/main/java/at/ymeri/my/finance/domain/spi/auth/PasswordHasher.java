package at.ymeri.my.finance.domain.spi.auth;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
