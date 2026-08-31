package at.ymeri.my.finance.domain.data.category;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class CategoryDto {

    private String id;
    private String name;
    private CategoryType type;
    private String color;
    private String parentCategoryId;
    private OffsetDateTime updatedAt;
}
