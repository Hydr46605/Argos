/**
 * The PKCE-first authentication subsystem: link building, Hydra challenge
 * resolution, credential submission, code exchange, refresh handling and the
 * explicit session state machine driving all of it.
 *
 * No member here logs or stringifies tokens; every failure path raises the
 * typed exceptions from `it.hydr4.argo.exceptions`.
 */
@file:Suppress("unused")

package it.hydr4.argo.auth
