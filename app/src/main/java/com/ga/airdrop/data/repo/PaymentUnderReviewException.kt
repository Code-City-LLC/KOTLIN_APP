package com.ga.airdrop.data.repo

/** The server's code for a payment the fraud rules HELD for staff review. */
internal const val PAYMENT_UNDER_REVIEW_CODE = "payment_under_review"

/**
 * A payment the fraud rules held for staff review (Laravel
 * `PaymentUnderReviewException`, 2026-09-15). It is NOT a failure: on the NCB
 * rail nothing was charged, on the Stripe rails the money is captured and
 * staff decide; the customer is notified either way. ViewModels title the
 * dialog "Payment under review" and must not invite a second payment.
 * [message] is the server's customer-facing text.
 */
internal class PaymentUnderReviewException(
    message: String,
    cause: Throwable? = null,
) : ApiException(message, cause)

internal val Throwable.isPaymentUnderReview: Boolean
    get() = this is PaymentUnderReviewException
