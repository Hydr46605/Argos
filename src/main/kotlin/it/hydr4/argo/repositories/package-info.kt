/**
 * One focused repository per Argo domain area, each mapping onto exactly one
 * family of [it.hydr4.argo.api.Endpoints] paths.
 *
 * All functions are `suspend`, throw only `it.hydr4.argo.exceptions.ArgoException`
 * subtypes and return immutable models already cleared of protocol mechanics.
 */
@file:Suppress("unused")

package it.hydr4.argo.repositories
