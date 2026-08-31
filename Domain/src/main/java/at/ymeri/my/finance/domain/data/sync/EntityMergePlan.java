package at.ymeri.my.finance.domain.data.sync;

import lombok.Data;

import java.util.List;

/**
 * One entity type's computed merge decision: what to insert, what to update (and which local row
 * each update targets), and how many incoming items needed no change at all.
 */
@Data
public class EntityMergePlan<T> {

    private List<T> toInsert;
    private List<MergeUpdate<T>> toUpdate;
    private int unchangedCount;

    public EntityMergeCounts toCounts() {
        EntityMergeCounts counts = new EntityMergeCounts();
        counts.setAdded(toInsert.size());
        counts.setUpdated(toUpdate.size());
        counts.setUnchanged(unchangedCount);
        return counts;
    }
}
