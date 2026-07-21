package com.danycli.assignmentchecker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FeeCacheStore {
    private const val PREFS_NAME = "secure_fee_cache"
    private const val KEY_SNAPSHOT_JSON = "fee_json"

    fun saveSnapshot(context: Context, snapshot: FeeSnapshot) {
        val payload = JSONObject().apply {
            put("cachedAtEpochMs", snapshot.cachedAtEpochMs)
            put("outstandingBalance", snapshot.outstandingBalance ?: JSONObject.NULL)
            put("totalCredits", snapshot.totalCredits ?: JSONObject.NULL)
            put("totalDebits", snapshot.totalDebits ?: JSONObject.NULL)
            put("lastTransactionDate", snapshot.lastTransactionDate ?: JSONObject.NULL)
            put("challans", snapshot.challans?.let { challansToJsonArray(it) } ?: JSONObject.NULL)
            put("ledger", snapshot.ledger?.let { ledgerToJsonArray(it) } ?: JSONObject.NULL)
            put("semesterFees", snapshot.semesterFees?.let { sectionRecordsToJsonArray(it) } ?: JSONObject.NULL)
            put("boardingFees", snapshot.boardingFees?.let { sectionRecordsToJsonArray(it) } ?: JSONObject.NULL)
            put("miscCharges", snapshot.miscCharges?.let { sectionRecordsToJsonArray(it) } ?: JSONObject.NULL)
            put("scholarships", snapshot.scholarships?.let { scholarshipsToJsonArray(it) } ?: JSONObject.NULL)
        }

        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .putString(KEY_SNAPSHOT_JSON, payload.toString())
            .apply()
    }

    fun loadSnapshot(context: Context): FeeSnapshot? {
        val raw = SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .getString(KEY_SNAPSHOT_JSON, null) ?: return null

        return runCatching {
            val json = JSONObject(raw)
            val challans = if (json.isNull("challans")) null else json.optJSONArray("challans").toChallanList()
            val ledger = if (json.isNull("ledger")) null else json.optJSONArray("ledger").toLedgerList()
            val semesterFees = if (json.isNull("semesterFees")) null else json.optJSONArray("semesterFees").toSectionRecordList()
            val boardingFees = if (json.isNull("boardingFees")) null else json.optJSONArray("boardingFees").toSectionRecordList()
            val miscCharges = if (json.isNull("miscCharges")) null else json.optJSONArray("miscCharges").toSectionRecordList()
            val scholarships = if (json.isNull("scholarships")) null else json.optJSONArray("scholarships").toScholarshipList()
            FeeSnapshot(
                outstandingBalance = if (json.isNull("outstandingBalance")) null else json.optDouble("outstandingBalance"),
                totalCredits = if (json.isNull("totalCredits")) null else json.optDouble("totalCredits"),
                totalDebits = if (json.isNull("totalDebits")) null else json.optDouble("totalDebits"),
                lastTransactionDate = if (json.isNull("lastTransactionDate")) null else json.optString("lastTransactionDate"),
                challans = challans,
                ledger = ledger,
                semesterFees = semesterFees,
                boardingFees = boardingFees,
                miscCharges = miscCharges,
                scholarships = scholarships,
                cachedAtEpochMs = json.optLong("cachedAtEpochMs", 0L)
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        SecurityUtils.getSecurePrefs(context, PREFS_NAME)
            .edit()
            .remove(KEY_SNAPSHOT_JSON)
            .apply()
    }

    private fun challansToJsonArray(challans: List<FeeChallan>): JSONArray {
        return JSONArray().apply {
            challans.forEach { item ->
                put(JSONObject().apply {
                    put("challanId", item.challanId)
                    put("semester", item.semester)
                    put("amount", item.amount)
                    put("dueDate", item.dueDate)
                    put("status", item.status)
                })
            }
        }
    }

    private fun ledgerToJsonArray(ledger: List<FeeLedgerEntry>): JSONArray {
        return JSONArray().apply {
            ledger.forEach { item ->
                put(JSONObject().apply {
                    put("date", item.date)
                    put("description", item.description)
                    put("amount", item.amount)
                    put("type", item.type)
                })
            }
        }
    }

    private fun sectionRecordsToJsonArray(records: List<FeeHistorySectionRecord>): JSONArray {
        return JSONArray().apply {
            records.forEach { item ->
                put(JSONObject().apply {
                    put("session", item.session)
                    put("feeType", item.feeType)
                    put("previousDues", item.previousDues)
                    put("semesterDues", item.semesterDues)
                    put("assistance", item.assistance)
                    put("assistancePaid", item.assistancePaid)
                    put("duesPaid", item.duesPaid)
                    put("refund", item.refund)
                    put("outstandingBalance", item.outstandingBalance)
                })
            }
        }
    }

    private fun scholarshipsToJsonArray(scholarships: List<ScholarshipRecord>): JSONArray {
        return JSONArray().apply {
            scholarships.forEach { item ->
                put(JSONObject().apply {
                    put("session", item.session)
                    put("feeType", item.feeType)
                    put("amount", item.amount)
                    put("type", item.type)
                })
            }
        }
    }

    private fun JSONArray?.toChallanList(): List<FeeChallan> {
        if (this == null) return emptyList()
        val result = mutableListOf<FeeChallan>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                FeeChallan(
                    challanId = item.optString("challanId", ""),
                    semester = item.optString("semester", ""),
                    amount = item.optDouble("amount", 0.0),
                    dueDate = item.optString("dueDate", ""),
                    status = item.optString("status", "Unpaid")
                )
            )
        }
        return result
    }

    private fun JSONArray?.toLedgerList(): List<FeeLedgerEntry> {
        if (this == null) return emptyList()
        val result = mutableListOf<FeeLedgerEntry>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                FeeLedgerEntry(
                    date = item.optString("date", ""),
                    description = item.optString("description", ""),
                    amount = item.optDouble("amount", 0.0),
                    type = item.optString("type", "Debit")
                )
            )
        }
        return result
    }

    private fun JSONArray?.toSectionRecordList(): List<FeeHistorySectionRecord> {
        if (this == null) return emptyList()
        val result = mutableListOf<FeeHistorySectionRecord>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                FeeHistorySectionRecord(
                    session = item.optString("session", ""),
                    feeType = item.optString("feeType", ""),
                    previousDues = item.optDouble("previousDues", 0.0),
                    semesterDues = item.optDouble("semesterDues", 0.0),
                    assistance = item.optDouble("assistance", 0.0),
                    assistancePaid = item.optDouble("assistancePaid", 0.0),
                    duesPaid = item.optDouble("duesPaid", 0.0),
                    refund = item.optDouble("refund", 0.0),
                    outstandingBalance = item.optDouble("outstandingBalance", 0.0)
                )
            )
        }
        return result
    }

    private fun JSONArray?.toScholarshipList(): List<ScholarshipRecord> {
        if (this == null) return emptyList()
        val result = mutableListOf<ScholarshipRecord>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            result.add(
                ScholarshipRecord(
                    session = item.optString("session", ""),
                    feeType = item.optString("feeType", ""),
                    amount = item.optDouble("amount", 0.0),
                    type = item.optString("type", "Scholarship Awarded")
                )
            )
        }
        return result
    }
}
