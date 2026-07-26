/**
 * Domain-specific failures raised by Argos.
 *
 * **Security invariant:** exception messages are built from user-visible strings
 * (paths, status codes, upstream `msg` fields) and must never embed bearer
 * tokens, refresh tokens, `x-auth-token` values, passwords or cookie payloads.
 * Tests enforce this across the whole suite.
 */
@file:Suppress("unused")

package it.hydr4.argo.exceptions
