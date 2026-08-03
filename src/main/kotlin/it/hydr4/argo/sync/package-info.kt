/**
 * Delta synchronization: protocol operation resolution (`I`/`D` markers), the
 * cheap `dashboard/what` change-probe and the decisions it drives.
 *
 * Semantics were ported from the reference implementation's `handleOperation`
 * utility and locked down by unit tests.
 */
@file:Suppress("unused")

package it.hydr4.argo.sync
