package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A fees installment from `listatassealunni`.
 *
 * Money amounts are wire strings because Argo returns decimal-formatted text
 * with locale-dependent separators; conversion belongs to consumers.
 *
 * @property installment Installment label ("Rata 1" observed).
 * @property description Reason/description of the fee.
 * @property debtor Debtor nominal.
 * @property iuv Unique payment identifier; nullable before payment slips are generated.
 * @property expectedAmount Amount due as raw string.
 * @property paidAmount Amount paid as raw string; nullable until payment completes.
 * @property paidOn Raw payment date; nullable until payment completes.
 */
@Serializable
public data class Pagamento(
    @SerialName("rata") public val installment: String? = null,
    @SerialName("descrizione") public val description: String? = null,
    @SerialName("debitore") public val debtor: String? = null,
    @SerialName("iuv") public val iuv: String? = null,
    @SerialName("stato") public val statusLabel: String? = null,
    @SerialName("status") public val statusCode: String? = null,
    @SerialName("importoPrevisto") public val expectedAmount: String? = null,
    @SerialName("importoPagato") public val paidAmount: String? = null,
    @SerialName("dataPagamento") public val paidOnRaw: String? = null,
    @SerialName("dataCreazione") public val createdRaw: String? = null,
    @SerialName("scadenza") public val deadlineRaw: String? = null,
    @SerialName("importoTassa") public val feeAmount: String? = null,
    @SerialName("listaSingoliPagamenti") public val singlePayments: List<SingoloPagamento>? = null,
    @SerialName("isPagoOnLine") public val onlinePayable: Boolean = false,
    @SerialName("pagabileOltreScadenza") public val payableAfterDeadline: Boolean = false,
    @SerialName("rptPresent") public val hasPaymentOrder: Boolean = false,
    @SerialName("rtPresent") public val hasReceipt: Boolean = false,
)

/** Component payment inside a [Pagamento]. */
@Serializable
public data class SingoloPagamento(
    @SerialName("importoTassa") public val feeAmount: String? = null,
    @SerialName("importoPrevisto") public val expectedAmount: String? = null,
    @SerialName("descrizione") public val description: String? = null,
)
