package com.malai.registrationseeder.data

// Human-readable descriptions for the canned transaction set. Kept separate from
// the postings list so a UI test screenshot can pin the exact strings ("Tesco
// — weekly shop", "TfL — daily commute", ...) and a copy tweak doesn't ripple
// through the dataset.
object UkMerchantsSeed {
    const val TESCO_WEEKLY: String = "Tesco — weekly shop"
    const val TESCO_TOPUP: String = "Tesco — top-up"
    const val TFL_COMMUTE: String = "TfL — daily commute"
    const val PRET: String = "Pret a Manger"
    const val OCTOPUS_DD: String = "Octopus Energy — DD"
    const val SPOTIFY_DD: String = "Spotify Premium — DD"
    const val COUNCIL_TAX_DD: String = "Council Tax — DD"
    const val RENT_SO: String = "Rent — SO"
    const val GYM_SO: String = "Gym membership — SO"
    const val SAVINGS_SWEEP: String = "Savings sweep"
    const val ACME_SALARY: String = "ACME Salary"
    const val SAVINGS_OPEN: String = "Savings opening balance"
    const val CC_PAYMENT: String = "Credit card payment"
    const val CASH_WITHDRAWAL: String = "Cash withdrawal"
    const val CORNER_SHOP: String = "Corner Shop"
    const val J_PATEL: String = "J Patel"
}
