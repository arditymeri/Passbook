package at.ymeri.my.finance.domain.data.auth;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AdminAccountDto {

    private String id;
    private String username;
    private String passwordHash;
    private int tokenVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
