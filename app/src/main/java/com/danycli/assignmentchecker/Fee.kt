package com.danycli.assignmentchecker

import androidx.compose.runtime.Immutable

@Immutable
data class FeeChallan(
    val challanId: String,
    val semester: String,
    val amount: Double,
    val dueDate: String,
    val status: String,
    val downloadLink: String = ""
)

@Immutable
data class FeeLedgerEntry(
    val date: String,
    val description: String,
    val amount: Double,
    val type: String // "Debit" or "Credit"
)

@Immutable
data class FeeHistorySectionRecord(
    val session: String,
    val feeType: String,
    val previousDues: Double,
    val semesterDues: Double,
    val assistance: Double,
    val assistancePaid: Double,
    val duesPaid: Double,
    val refund: Double,
    val outstandingBalance: Double
)

@Immutable
data class ScholarshipRecord(
    val session: String,
    val feeType: String,
    val amount: Double,
    val type: String // "Scholarship Awarded" or "Credit Adjustment"
)

@Immutable
data class FeeSnapshot(
    val outstandingBalance: Double?,
    val totalCredits: Double?,
    val totalDebits: Double?,
    val lastTransactionDate: String?,
    val challans: List<FeeChallan>?,
    val ledger: List<FeeLedgerEntry>?,
    val semesterFees: List<FeeHistorySectionRecord>?,
    val boardingFees: List<FeeHistorySectionRecord>?,
    val miscCharges: List<FeeHistorySectionRecord>?,
    val scholarships: List<ScholarshipRecord>?,
    val cachedAtEpochMs: Long
)
