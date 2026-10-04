package com.vivekray898.payvoice.replay

/**
 * Step 3 replay corpus: real-format Google Pay notifications.
 *
 * These are the shapes GPay actually posts in India, transcribed from on-device
 * captures and from the parser's own documented vocabulary. They exist to be
 * **replayed through the real [com.vivekray898.payvoice.PaymentPipeline]** on a
 * device, so the pipeline — not a copy of it — decides what happens.
 *
 * [kind] states what the notification *is in the real world*, independently of
 * what the parser does with it. The harness records the actual outcome and the
 * two are compared in `docs/PAYMENT_PIPELINE_FINDINGS.md`. Nothing here encodes
 * a wanted answer: a fixture marked [Kind.Credit] that the pipeline drops is a
 * finding, not a broken test.
 *
 * Which extra carried the payment line varies by GPay version and by the
 * collapsed/expanded notification state, so the fixtures deliberately cover all
 * four extras, including collapsed `text` with the full line only in `bigText`.
 */
object PaymentFixtures {

    /**
     * What the notification means in the real world.
     *
     * [Credit] is the only kind that must be announced. [NonPayment] and
     * [Debit] reaching the speaker would be a false announcement. [Ambiguous]
     * is a real notification whose intent a human could not call from the text
     * alone — the interesting bucket for "did the gate choose well".
     */
    enum class Kind {
        /** Money arriving. Must be announced. */
        Credit,

        /** Money leaving. Must never be announced. */
        Debit,

        /** Promotions, balance nudges, requests, failures. Must never be announced. */
        NonPayment,

        /** Real notification a human could not classify from the text alone. */
        Ambiguous,
    }

    /**
     * One notification as the listener sees it: four extras, any of which may
     * be null, exactly as the platform delivers them.
     */
    data class Fixture(
        val id: String,
        val kind: Kind,
        val title: String?,
        val text: String?,
        val bigText: String? = null,
        val subText: String? = null,
        val note: String = "",
    ) {
        /** True when only a collapsed/auxiliary extra carries the payment line. */
        val paymentLineHiddenFromText: Boolean =
            text.isNullOrBlank() && (bigText?.isNotBlank() == true || subText?.isNotBlank() == true)
    }

    /** Credits in the plain, well-formed formats. */
    private val credits = listOf(
        Fixture("c01", Kind.Credit, "Payment received", "₹500 received from Rahul Sharma"),
        Fixture("c02", Kind.Credit, "Payment received", "You received ₹1,200.50 from Amit"),
        Fixture("c03", Kind.Credit, "Money received", "₹200 received from Priya • UPI Ref 512345678901"),
        Fixture("c04", Kind.Credit, "Payment received", "BINAY PAL paid you ₹1.00", note = "real 'paid you' credit form"),
        Fixture("c05", Kind.Credit, "Payment received", "₹99 received from Amazon Pay"),
        Fixture("c06", Kind.Credit, "Payment received", "Amit paid you ₹250 for Coffee"),
        Fixture("c07", Kind.Credit, "Received", "₹1,499 from Zomato"),
        Fixture("c08", Kind.Credit, "Payment", "Received ₹640\nTxn ID: 412233445566", note = "amount on line 2"),
        Fixture("c09", Kind.Credit, "Payment received", "You received ₹5,000 from Suresh Kumar"),
        Fixture("c10", Kind.Credit, "Payment received", "₹100 received from Kiran", subText = "Via UPI • HDFC Bank"),
        Fixture("c11", Kind.Credit, "Payment received", "₹65 received from Meera Iyer"),
        Fixture("c12", Kind.Credit, "Payment received", "₹1 received from Rahul Sharma"),
        Fixture("c13", Kind.Credit, "Payment received", "₹999 received from Deepak"),
        Fixture("c14", Kind.Credit, "Payment received", "You received ₹49.90 from Ravi Teja"),
    )

    /** Credits in formats that historically break parsers. */
    private val awkwardCredits = listOf(
        Fixture("x01", Kind.Credit, "Payment received", "Rs. 750 received from Suresh", note = "Rs. with dot"),
        Fixture("x02", Kind.Credit, "Payment received", "INR 850 received from Anjali", note = "INR spelled out"),
        Fixture("x03", Kind.Credit, "Payment received", "₹5,00,000 received from Payroll", note = "lakh grouping"),
        Fixture("x04", Kind.Credit, "Payment received", "₹12,34,567 received from Insurance Co", note = "crore grouping"),
        Fixture("x05", Kind.Credit, "₹300 received", "from Neha", note = "amount in the TITLE, sender in text"),
        Fixture("x06", Kind.Credit, null, "₹700 received from Vivek", note = "no title at all"),
        Fixture("x07", Kind.Credit, "Payment received", "₹…", bigText = "₹700 received from Vivek", note = "collapsed text, full line in bigText"),
        Fixture("x08", Kind.Credit, "Payment received", null, bigText = "₹850 received from Sneha", note = "payment line only in bigText"),
        Fixture("x09", Kind.Credit, "Payment received", null, subText = "₹950 received from Tarun", note = "payment line only in subText"),
        Fixture("x10", Kind.Credit, "Payment received", "₹3,00 received from Anil • UPI ID 1234567890@paytm"),
        Fixture("x11", Kind.Credit, "Payment received", "You received ₹2,500 from Rahul Sharma on 4 Oct"),
        Fixture("x12", Kind.Credit, "payment received", "₹150 received from Qasim", note = "lower-case title"),
        Fixture("x13", Kind.Credit, "PAYMENT RECEIVED", "₹175 RECEIVED FROM LAXMI", note = "upper case throughout"),
        Fixture("x14", Kind.Credit, "Payment received", "₹500 received from Rahul Sharma.", note = "trailing full stop"),
        Fixture("x15", Kind.Credit, "Payment received", "₹500 received from Rahul Sharma • To your HDFC Bank A/c ending 4321"),
        Fixture("x16", Kind.Credit, "Payment received", "₹1 received", subText = "from Ravi", note = "amount in text, sender in subText"),
        Fixture("x17", Kind.Credit, "Payment received", "UPI - ₹450 received from Aditi"),
        Fixture("x18", Kind.Credit, "Payment received", "₹ 600 received from Harish", note = "space after the rupee sign"),
        Fixture("x19", Kind.Credit, "Payment received", "₹725.50 received from Kavya", note = "paise"),
        Fixture("x20", Kind.Credit, "Payment received", "You have received ₹2,000 from Nisha", bigText = "You have received ₹2,000 from Nisha • Ref 998877665544"),
    )

    /** Credits with no reference id — these are what the 5-minute bucket eats. */
    private val referenceLessCredits = listOf(
        Fixture("r01", Kind.Credit, "Payment received", "₹500 received from Rahul Sharma"),
        Fixture("r02", Kind.Credit, "Payment received", "₹500 received from Rahul Sharma", note = "same amount, same sender — a genuine second payment"),
        Fixture("r03", Kind.Credit, "Payment received", "₹500 received from Rahul Sharma", note = "third in the same 5-minute bucket"),
    )

    /** Debits: real money leaving. Announcing any of these is a false alarm. */
    private val debits = listOf(
        Fixture("d01", Kind.Debit, "Payment sent", "₹500 paid to Big Basket"),
        Fixture("d02", Kind.Debit, "Payment sent", "You paid ₹1,200 to Swiggy"),
        Fixture("d03", Kind.Debit, "Payment sent", "₹45 paid to Ola"),
        Fixture("d04", Kind.Debit, "Money sent", "₹250 sent to Rahul Sharma"),
        Fixture("d05", Kind.Debit, "Payment sent", "You paid ₹99 to Amazon"),
        Fixture("d06", Kind.Debit, "Cash out", "₹500 sent to your HDFC Bank A/c"),
        Fixture("d07", Kind.Debit, "Payment sent", "₹60 paid for a recharge"),
    )

    /** Non-payments: promotions, nudges, requests, failures. */
    private val nonPayments = listOf(
        Fixture("n01", Kind.NonPayment, "Google Pay", "You have a cashback offer on your next payment"),
        Fixture("n02", Kind.NonPayment, "Google Pay", "Invited by Amit — scratch card and win up to ₹1,000"),
        Fixture("n03", Kind.NonPayment, "Google Pay", "You have ₹0 in your GPay balance", note = "currency marker, no payment"),
        Fixture("n04", Kind.NonPayment, "Google Pay", "Check your GPay balance and transactions"),
        Fixture("n05", Kind.NonPayment, "Request", "Rahul sent you a request for ₹500"),
        Fixture("n06", Kind.NonPayment, "Payment failed", "Payment of ₹500 to Shop failed"),
        Fixture("n07", Kind.NonPayment, "Google Pay", "Your offer ends today — reward up to ₹200"),
        Fixture("n08", Kind.NonPayment, "Google Pay", "Verify your number to receive payments"),
        Fixture("n09", Kind.NonPayment, "Reminder", "You have a pending payment request of ₹300"),
        Fixture("n10", Kind.NonPayment, "Google Pay", "Rate us and earn ₹50 reward"),
        Fixture("n11", Kind.NonPayment, "Scan and pay", "Scan any QR code to pay ₹750"),
        Fixture("n12", Kind.NonPayment, "Google Pay", "Add money offer: get ₹10 back on your first recharge"),
    )

    /** Real notifications whose intent is not decidable from the text. */
    private val ambiguous = listOf(
        Fixture("a01", Kind.Ambiguous, "Payment received", "₹500 refunded to you by Flipkart"),
        Fixture("a02", Kind.Ambiguous, "Payment received", "₹500 credited back to your account"),
        Fixture("a03", Kind.Ambiguous, "Payment received", "₹500 received from Rahul and sent to Priya"),
        Fixture("a04", Kind.Ambiguous, "Google Pay", "₹500 cashback credited to your wallet"),
        Fixture("a05", Kind.Ambiguous, "Payment received", "₹500 received from Rahul Sharma (pending)"),
        Fixture("a06", Kind.Ambiguous, "Payment received", "You received ₹500 from a UPI ID"),
        Fixture("a07", Kind.Ambiguous, "Payment received", "₹500 • Rahul Sharma", note = "no directional verb at all"),
        Fixture("a08", Kind.Ambiguous, "Payment received", "₹500 received from Rahul Sharma. ₹20 fee charged."),
        Fixture("a09", Kind.Ambiguous, "Payment received", "₹ received from Rahul Sharma", note = "marker with no digits"),
        Fixture("a10", Kind.Ambiguous, "Payment received", "Received payment of ₹500", note = "no sender at all"),
    )

    /** The full corpus: 66 fixtures. */
    val all: List<Fixture> =
        credits + awkwardCredits + referenceLessCredits + debits + nonPayments + ambiguous

    /** Convenience views used by individual scenarios. */
    val plainCredits: List<Fixture> = credits
    val referenceLess: List<Fixture> = referenceLessCredits
    val debitsOnly: List<Fixture> = debits
    val nonPaymentsOnly: List<Fixture> = nonPayments
    val ambiguousOnly: List<Fixture> = ambiguous

    /** A burst of [count] distinct credits, one every [gapMs]. */
    fun burst(count: Int, gapMs: Long, basePostedAtMs: Long): List<Pair<Long, Fixture>> {
        val templates = listOf(
            "₹%d received from Rahul Sharma • UPI Ref %s",
            "₹%d received from Priya Verma • UPI Ref %s",
            "₹%d received from Amit Kumar • UPI Ref %s",
        )
        return (1..count).map { i ->
            val amount = 100 * i
            val ref = (512345678900L + i).toString()
            val body = String.format(templates[i % templates.size], amount, ref)
            basePostedAtMs + (i - 1) * gapMs to Fixture(
                id = "burst%02d".format(i),
                kind = Kind.Credit,
                title = "Payment received",
                text = body,
            )
        }
    }
}