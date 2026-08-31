package at.ymeri.my.finance.domain.data.sync;

import lombok.Data;

/** The user-facing view of one entity type's slice of a {@link MergePlanDto}. */
@Data
public class EntityMergeCounts {

    private int added;
    private int updated;
    private int unchanged;
}
