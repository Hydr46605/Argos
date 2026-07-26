/**
 * Transport machinery: engine abstraction (`ArgoHttpEngine`), OkHttp wiring and
 * the header-authenticated client used by every repository.
 *
 * The engine split exists so tests replay recorded fixtures deterministically
 * through `FakeEngine` instead of hitting the wire.
 */
@file:Suppress("unused")

package it.hydr4.argo.api
