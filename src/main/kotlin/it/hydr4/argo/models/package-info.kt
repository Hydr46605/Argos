/**
 * Typed representations of the Argo ScuolaNext API payloads.
 *
 * Models are intentionally split per domain concept instead of mirroring the huge
 * merged dashboard document; aggregation happens once, in the Dashboard model.
 * Fields that are optional or unstable upstream are declared nullable with a KDoc
 * warning explaining the observed instability.
 */
@file:Suppress("unused")

package it.hydr4.argo.models
