package it.hydr4.argo.sync

/**
 * Applies Argo's delta-operation lists onto locally held collections.
 *
 * Wire arrays carry inserts/updates plus occasional `{operazione: "D", pk}`
 * tombstones that must be merged into the previous snapshot instead of replacing
 * it wholesale. This is the Kotlin port of the reference client's
 * `handleOperation` routine, simplified to well-defined list semantics.
 *
 * @property identityOf Extracts the stable identity used for update/delete matching.
 */
public object DeltaLists {
    /**
     * Merges [incoming] into [previous].
     *
     * Order of application: tombstones last, mirroring the reference so fresh
     * inserts whose ids overlap deletions survive exactly like upstream behaves.
     *
     * @param isTombstone Marks entries carrying deletion intents.
     * @return The merged immutable list preserving insertion order otherwise.
     */
    public fun <T> apply(previous: List<T>, incoming: List<T>, identityOf: (T) -> String?, isTombstone: (T) -> Boolean): List<T> {
        val result = previous.toMutableList()
        val deletions = mutableListOf<String>()

        for (entry in incoming) {
            if (isTombstone(entry)) {
                identityOf(entry)?.let(deletions::add)
                continue
            }
            val id = identityOf(entry)
            val index =
                id?.let { existing ->
                    result.indexOfFirst { identityOf(it) == existing }
                } ?: -1
            if (index >= 0) result[index] = entry else result += entry
        }

        return result.filterNot { entry ->
            val id = identityOf(entry)
            id != null && id in deletions
        }
    }
}
