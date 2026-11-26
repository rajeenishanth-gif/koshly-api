package com.koshly.account;

import java.time.Instant;
import java.util.UUID;

/**
 * Reply to DELETE /api/accounts/{id}.
 *
 * @param deleteAfter when it will be deleted for good
 * @param canUndo     false for Plaid-linked accounts: they were disconnected
 *                    from Plaid, so they have to be linked again instead
 */
public record RemovedAccountResponse(UUID id, String name, Instant removedAt, Instant deleteAfter, boolean canUndo) {
}
