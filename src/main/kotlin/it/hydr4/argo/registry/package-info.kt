/**
 * Registration and extension point for custom endpoints.
 *
 * Argo's wire surface is undocumented and evolves per school deployment; this
 * package lets consumers declare additional endpoints and call them through the
 * same typed, authenticated transport as the built-in repositories.
 */
@file:Suppress("unused")

package it.hydr4.argo.registry
