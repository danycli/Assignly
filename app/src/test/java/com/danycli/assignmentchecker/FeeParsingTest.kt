package com.danycli.assignmentchecker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Method

class FeeParsingTest {

    private val repository = PortalRepository()

    private fun invokeParseFeeDetails(challansHtml: String?, historyHtml: String?): FeeSnapshot {
        val method: Method = PortalRepository::class.java.getDeclaredMethod(
            "parseFeeDetailsFromHtml",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(repository, challansHtml, historyHtml) as FeeSnapshot
    }

    @Test
    fun testParseFeeHistory() {
        val historyHtml = """
            <table>
                <tr>
                    <th>S.No</th>
                    <th>Session</th>
                    <th>Particulars</th>
                    <th>Cr Hour</th>
                    <th>Semester Fee</th>
                    <th>Scholarship</th>
                    <th>Assistance</th>
                    <th>Paid</th>
                    <th>Receipt No</th>
                    <th>Outstanding</th>
                    <th>Status</th>
                </tr>
                <tr>
                    <td>1</td>
                    <td>Spring 2026</td>
                    <td>Tuition Fee</td>
                    <td>15</td>
                    <td>50,000</td>
                    <td>10,000</td>
                    <td>0</td>
                    <td>40,000</td>
                    <td>REC-1002</td>
                    <td>0</td>
                    <td>Paid</td>
                </tr>
            </table>
        """.trimIndent()

        val challansHtml = """
            <table>
                <tr>
                    <td>Spring 2026</td>
                    <td>Challan No: 123456</td>
                    <td>Due Date: 15-Jun-2026</td>
                    <td>Amount: 50,000</td>
                    <td><a href="javascript:__doPostBack('btnDownload', '')">Download</a></td>
                </tr>
            </table>
        """.trimIndent()

        val snapshot = invokeParseFeeDetails(challansHtml, historyHtml)
        assertNotNull(snapshot)
        assertEquals(0.0, snapshot.outstandingBalance ?: -1.0, 0.001)
        assertEquals(50000.0, snapshot.totalDebits ?: -1.0, 0.001)
        assertEquals(50000.0, snapshot.totalCredits ?: -1.0, 0.001)
        
        assertNotNull(snapshot.challans)
        assertEquals(1, snapshot.challans!!.size)
        assertEquals("Spring 2026", snapshot.challans!![0].semester)
        assertEquals("123456", snapshot.challans!![0].challanId)
        val expectedLink = "portal-postback:@btnDownload||" + java.net.URLEncoder.encode(
            "${com.danycli.assignmentchecker.BuildConfig.PORTAL_BASE_URL}/FeeChallans.aspx",
            "UTF-8"
        )
        assertEquals(expectedLink, snapshot.challans!![0].downloadLink)
        
        assertNotNull(snapshot.semesterFees)
        assertEquals(1, snapshot.semesterFees!!.size)
        assertEquals("Spring 2026", snapshot.semesterFees!![0].session)
        assertEquals(50000.0, snapshot.semesterFees!![0].semesterDues, 0.001)
        
        assertNotNull(snapshot.scholarships)
        assertEquals(1, snapshot.scholarships!!.size)
        assertEquals(10000.0, snapshot.scholarships!![0].amount, 0.001)
        assertEquals("Scholarship Awarded", snapshot.scholarships!![0].type)
        
        assertNotNull(snapshot.ledger)
        assertEquals(3, snapshot.ledger!!.size) // 1 Debit (Billed), 2 Credits (Scholarship, Payment)
        assertEquals("Debit", snapshot.ledger!!.firstOrNull { it.description.contains("Billed") }?.type)
        assertEquals("Credit", snapshot.ledger!!.firstOrNull { it.description.contains("Paid") }?.type)
    }

    @Test
    fun testParseRealHtml() {
        val challansFile = java.io.File("C:\\Users\\sahib\\.gemini\\antigravity\\brain\\363351be-a111-4c1d-b0b5-fac8dd5cb9fd\\scratch\\fee_challans.html")
        val historyFile = java.io.File("C:\\Users\\sahib\\.gemini\\antigravity\\brain\\363351be-a111-4c1d-b0b5-fac8dd5cb9fd\\scratch\\fee_history.html")
        
        val challansHtml = if (challansFile.exists()) challansFile.readText() else null
        val historyHtml = if (historyFile.exists()) historyFile.readText() else null
        
        val snapshot = invokeParseFeeDetails(challansHtml, historyHtml)
        assertNotNull(snapshot)
        
        println("REAL SNAPSHOT PARSING RESULT:")
        println("Outstanding Balance: ${snapshot.outstandingBalance}")
        println("Total Debits: ${snapshot.totalDebits}")
        println("Total Credits: ${snapshot.totalCredits}")
        println("Challans size: ${snapshot.challans?.size}")
        println("Ledger size: ${snapshot.ledger?.size}")
        
        snapshot.ledger?.forEach { entry ->
            println("Ledger Entry: date=${entry.date}, desc=${entry.description}, amount=${entry.amount}, type=${entry.type}")
        }
        
        println("SEMESTER FEES RECORDS:")
        snapshot.semesterFees?.forEach { record ->
            println("Semester: ${record.session}, Billed: ${record.semesterDues}, Scholarship (Assistance Paid): ${record.assistancePaid}, Assistance: ${record.assistance}, Paid: ${record.duesPaid}, Outstanding: ${record.outstandingBalance}")
        }
    }
}

