package com.danycli.assignmentchecker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileContactParsingTest {

    private val repository = PortalRepository()

    @Test
    fun testParseContactInfo_completeMobileAndEmail() {
        val html = """
            <html>
            <body>
                <select name="ddlServiceNo">
                    <option value="0300" selected>0300</option>
                </select>
                <input type="text" name="txtCellNo" value="1234567" />
                <input type="text" name="txtEmail" value="student@gmail.com" />
            </body>
            </html>
        """.trimIndent()

        val result = repository.parseContactInfo(html)
        assertEquals("student@gmail.com", result.first)
        assertEquals("03001234567", result.second)
    }

    @Test
    fun testParseContactInfo_incompletePhoneOnlyPrefix() {
        val html = """
            <html>
            <body>
                <select name="ddlNetwork">
                    <option value="0333" selected>0333</option>
                </select>
                <input type="text" name="txtCellNo" value="" />
                <input type="text" name="txtEmail" value="student@gmail.com" />
            </body>
            </html>
        """.trimIndent()

        val result = repository.parseContactInfo(html)
        assertEquals("student@gmail.com", result.first)
        assertEquals("", result.second) // Should be discarded because combined length is 4 (< 9)
    }

    @Test
    fun testParseContactInfo_nameWithPhone() {
        val html = """
            <html>
            <body>
                <select name="ctl00${'$'}ContentPlaceHolder1${'$'}ddlServiceNo">
                    <option value="0321" selected>0321</option>
                </select>
                <input type="text" name="ctl00${'$'}ContentPlaceHolder1${'$'}txtPhone" value="7654321" />
            </body>
            </html>
        """.trimIndent()

        val result = repository.parseContactInfo(html)
        assertEquals("03217654321", result.second)
    }

    @Test
    fun testParseContactInfo_singleNumberField() {
        val html = """
            <html>
            <body>
                <input type="text" name="txtMobile" value="03459876543" />
            </body>
            </html>
        """.trimIndent()

        val result = repository.parseContactInfo(html)
        assertEquals("03459876543", result.second) // Should succeed because length >= 9
    }

    @Test
    fun testHasActiveScholarshipRows_onlyHeader() {
        val html = """
            <table>
                <tr>
                    <td>S.No</td>
                    <td>Scholarship Name</td>
                    <td>Scholarship Status</td>
                </tr>
            </table>
        """.trimIndent()
        val result = repository.hasActiveScholarshipRows(html)
        org.junit.Assert.assertFalse(result)
    }

    @Test
    fun testHasActiveScholarshipRows_noRecord() {
        val html = """
            <table>
                <tr>
                    <td>S.No</td>
                    <td>Scholarship Name</td>
                    <td>Scholarship Status</td>
                </tr>
                <tr>
                    <td colspan="3">No Record Found</td>
                </tr>
            </table>
        """.trimIndent()
        val result = repository.hasActiveScholarshipRows(html)
        org.junit.Assert.assertFalse(result)
    }

    @Test
    fun testHasActiveScholarshipRows_activeScholarship() {
        val html = """
            <table>
                <tr>
                    <td>S.No</td>
                    <td>Scholarship Name</td>
                    <td>Scholarship Status</td>
                </tr>
                <tr>
                    <td>1</td>
                    <td>HEC Need Based</td>
                    <td>Active</td>
                </tr>
            </table>
        """.trimIndent()
        val result = repository.hasActiveScholarshipRows(html)
        org.junit.Assert.assertTrue(result)
    }

    @Test
    fun testParseFullStudentProfile_allFields() {
        val html = """
            <html>
            <body>
                <span id="lblStudentName">John Doe</span>
                <span id="lblRegNo">FA20-BCS-001</span>
                <span id="lblProgram">BS Computer Science</span>
                <span id="lblScholarshipStatus">Yes</span>
                <span id="lblDOB">01/01/2000</span>
                <span id="lblMobile">03001122333</span>
                <span id="lblPhone">04233334444</span>
            </body>
            </html>
        """.trimIndent()
        val result = repository.parseFullStudentProfileFromHtml(html)
        assertEquals("Yes", result.scholarshipStatus)
        assertEquals("01/01/2000", result.dob)
        assertEquals("03001122333", result.mobile)
        assertEquals("04233334444", result.phone)
    }

    @Test
    fun testParseContactInfo_withCellDetailDropdown() {
        val html = """
            <html>
            <body>
                <input name="ctl00${'$'}DataContent${'$'}txtServiceNo" type="text" value="0329" />
                <input name="ctl00${'$'}DataContent${'$'}txtCellNo" type="text" value="5832130" />
                <select name="ctl00${'$'}DataContent${'$'}ddlCellNoDetail">
                    <option selected="selected" value="Personal">Personal</option>
                </select>
                <input name="ctl00${'$'}DataContent${'$'}txtEmail" type="text" value="danysahibzada@gmail.com" />
            </body>
            </html>
        """.trimIndent()

        val result = repository.parseContactInfo(html)
        assertEquals("danysahibzada@gmail.com", result.first)
        assertEquals("03295832130", result.second)
    }

    @Test
    fun testExtractPasswordRules_orderedList() {
        val html = """
            <html>
            <body>
                <div class="notification information png_bg">
                    <div>
                        <h4 style="color: red">Note : NEW PASSWORD POLICY</h4>
                        <ol>
                            <li>At least 8 characters.</li>
                            <li>At least One number (digit).</li>
                            <li>At least One special character.</li>
                            <li>At least One Upper Case letter.</li>
                            <li>At least One Lower Case letter.</li>
                        </ol>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val rules = repository.extractPasswordRules(html)
        val expected = "Note: New Password Policy\n" +
                "• At least 8 characters.\n" +
                "• At least One number (digit).\n" +
                "• At least One special character.\n" +
                "• At least One Upper Case letter.\n" +
                "• At least One Lower Case letter."
        assertEquals(expected, rules)
    }

    @Test
    fun testParsePasswordChangeResult_successMessage() {
        val html = """
            <html>
            <body>
                <span id="ctl00_DataContent_lblMsg" style="color:Green;">Password changed successfully.</span>
                <input name="txtOldPassword" type="password" />
            </body>
            </html>
        """.trimIndent()
        val result = repository.parsePasswordChangeResult(html)
        assertTrue(result.isSuccess)
        assertEquals("Password changed successfully.", result.getOrThrow())
    }

    @Test
    fun testParsePasswordChangeResult_incorrectOldPassword() {
        val html = """
            <html>
            <body>
                <span id="ctl00_DataContent_cvOldPassword" class="text-danger">Incorrect old password.</span>
                <input name="txtOldPassword" type="password" />
            </body>
            </html>
        """.trimIndent()
        val result = repository.parsePasswordChangeResult(html)
        assertFalse(result.isSuccess)
        assertEquals("Incorrect old password.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testParsePasswordChangeResult_notChanged() {
        val html = """
            <html>
            <body>
                <span id="ctl00_DataContent_lblError" class="text-danger">Password could not be changed.</span>
                <input name="txtOldPassword" type="password" />
            </body>
            </html>
        """.trimIndent()
        val result = repository.parsePasswordChangeResult(html)
        assertFalse(result.isSuccess)
        assertEquals("Password could not be changed.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testParsePasswordChangeResult_noMessageFieldsPresent() {
        val html = """
            <html>
            <body>
                <input name="txtOldPassword" type="password" />
            </body>
            </html>
        """.trimIndent()
        val result = repository.parsePasswordChangeResult(html)
        assertFalse(result.isSuccess)
        assertEquals("Incorrect current password or invalid password change request.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testParsePasswordChangeResult_noMessageFieldsMissing() {
        val html = """
            <html>
            <body>
                <div>Some other page content on success.</div>
            </body>
            </html>
        """.trimIndent()
        val result = repository.parsePasswordChangeResult(html)
        assertTrue(result.isSuccess)
        assertEquals("Password changed successfully.", result.getOrThrow())
    }

    @Test
    fun testParsePasswordChangeResult_withNoscriptJavascriptWarning() {
        val html = """
            <html>
            <body>
                <noscript>
                    <div class="notification error">Javascript is disabled or is not supported by your browser. Please upgrade your browser or enable Javascript to navigate the interface properly.</div>
                </noscript>
                <span id="ctl00_DataContent_lblMsg" style="color:Green;">Password changed successfully.</span>
                <input name="txtOldPassword" type="password" />
            </body>
            </html>
        """.trimIndent()
        val result = repository.parsePasswordChangeResult(html)
        assertTrue(result.isSuccess)
        assertEquals("Password changed successfully.", result.getOrThrow())
    }

    @Test
    fun testParsePasswordChangeResult_successWithHiddenValidators() {
        val html = """
            <html>
            <body>
                <span id="ctl00_DataContent_cvOldPassword" class="text-danger" style="display:none;">Incorrect old password.</span>
                <span id="ctl00_DataContent_rfvNewPassword" class="text-danger" style="display: none;">New password is required.</span>
                <span id="ctl00_DataContent_lblMsg" style="color:Green;">Password changed successfully.</span>
                <input name="txtOldPassword" type="password" />
            </body>
            </html>
        """.trimIndent()
        val result = repository.parsePasswordChangeResult(html)
        assertTrue(result.isSuccess)
        assertEquals("Password changed successfully.", result.getOrThrow())
    }
}
