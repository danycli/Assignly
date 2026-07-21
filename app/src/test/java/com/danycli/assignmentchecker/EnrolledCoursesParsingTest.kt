package com.danycli.assignmentchecker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Method

class EnrolledCoursesParsingTest {

    private val repository = PortalRepository()

    private fun invokeParseEnrolledCourses(html: String): EnrolledCoursesData {
        val method: Method = PortalRepository::class.java.getDeclaredMethod("parseEnrolledCoursesFromHtml", String::class.java)
        method.isAccessible = true
        return method.invoke(repository, html) as EnrolledCoursesData
    }

    @Test
    fun testStandardParsing() {
        val html = """
            <html>
            <body>
                <h2>Current Semester: Spring 2026</h2>
                <table>
                    <tr>
                        <th>Course Code</th>
                        <th>Subject Title</th>
                        <th>Cr.Hr</th>
                        <th>Section</th>
                        <th>Instructor</th>
                    </tr>
                    <tr>
                        <td>CSC211</td>
                        <td>Data Structures</td>
                        <td>4 (3,1)</td>
                        <td>C</td>
                        <td>Dr. Ahmed Khan</td>
                    </tr>
                    <tr>
                        <td>HUM100</td>
                        <td>English Composition</td>
                        <td>3 (3,0)</td>
                        <td>A</td>
                        <td>Miss Sadia</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = invokeParseEnrolledCourses(html)
        assertNotNull(result)
        assertEquals("Spring 2026", result.semesterName)
        assertEquals(2, result.courses.size)

        val ds = result.courses.find { it.courseCode == "CSC211" }
        assertNotNull(ds)
        assertEquals("Data Structures", ds!!.courseTitle)
        assertEquals("4 (3,1)", ds.creditHours)
        assertEquals("C", ds.section)
        assertEquals("Dr. Ahmed Khan", ds.instructorName)

        val eng = result.courses.find { it.courseCode == "HUM100" }
        assertNotNull(eng)
        assertEquals("English Composition", eng!!.courseTitle)
        assertEquals("3 (3,0)", eng.creditHours)
        assertEquals("A", eng.section)
        assertEquals("Miss Sadia", eng.instructorName)
    }

    @Test
    fun testHeaderMappingAndWhitespaceNormalization() {
        val html = """
            <html>
            <body>
                <h3>Spring 2026</h3>
                <table>
                    <thead>
                        <tr>
                            <td>  CLASS  </td>
                            <td>  FACULTY MEMBER  </td>
                            <td>  CR  </td>
                            <td>  SUBJECT  </td>
                            <td>  CODE  </td>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>  B  </td>
                            <td>  Dr. John  Doe  </td>
                            <td>  4  </td>
                            <td>  Object   Oriented   Programming  </td>
                            <td>  CSC102  </td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = invokeParseEnrolledCourses(html)
        assertEquals("Spring 2026", result.semesterName)
        assertEquals(1, result.courses.size)
        
        val course = result.courses[0]
        assertEquals("CSC102", course.courseCode)
        // Check whitespace normalization: double spaces should be single spaces
        assertEquals("Object Oriented Programming", course.courseTitle)
        assertEquals("4", course.creditHours)
        assertEquals("B", course.section)
        assertEquals("Dr. John Doe", course.instructorName)
    }

    @Test
    fun testHandleMissingSectionAndEmptyInstructor() {
        val html = """
            <html>
            <body>
                <h2>Fall 2025</h2>
                <table>
                    <tr>
                        <th>Code</th>
                        <th>Subject</th>
                        <th>Instructor</th>
                    </tr>
                    <tr>
                        <td>CSC301</td>
                        <td>Theory of Automata</td>
                        <td></td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = invokeParseEnrolledCourses(html)
        assertEquals("Fall 2025", result.semesterName)
        assertEquals(1, result.courses.size)
        
        val course = result.courses[0]
        assertEquals("CSC301", course.courseCode)
        assertEquals("Theory of Automata", course.courseTitle)
        assertEquals("", course.section) // missing column handles gracefully as empty string
        assertEquals("", course.instructorName) // empty instructor handles gracefully
    }

    @Test
    fun testIgnoreHistoricalAndPickLatestSemester() {
        val html = """
            <html>
            <body>
                <h2>Fall 2025</h2>
                <table>
                    <tr>
                        <th>Code</th>
                        <th>Title</th>
                    </tr>
                    <tr>
                        <td>CSC201</td>
                        <td>Discrete Structures</td>
                    </tr>
                </table>

                <h2>Spring 2026</h2>
                <table>
                    <tr>
                        <th>Code</th>
                        <th>Title</th>
                    </tr>
                    <tr>
                        <td>CSC301</td>
                        <td>Automata Theory</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = invokeParseEnrolledCourses(html)
        assertEquals("Spring 2026", result.semesterName)
        assertEquals(1, result.courses.size)
        assertEquals("CSC301", result.courses[0].courseCode)
        assertEquals("Automata Theory", result.courses[0].courseTitle)
    }

    @Test
    fun testEmptyOrNoCoursesThrowsException() {
        val html = """
            <html>
            <body>
                <p>No records found.</p>
            </body>
            </html>
        """.trimIndent()

        try {
            invokeParseEnrolledCourses(html)
            fail("Expected exception not thrown")
        } catch (e: Exception) {
            val cause = e.cause ?: e
            assertTrue(cause is PortalSystemException)
            assertEquals("No enrolled courses found for the current semester.", cause.message)
        }
    }

    @Test
    fun testSummaryPageStyleParsingWithMapResolution() {
        val titleCodeField = PortalRepository::class.java.getDeclaredField("courseTitleToCodeMap")
        titleCodeField.isAccessible = true
        val titleCodeMap = titleCodeField.get(repository) as MutableMap<String, String>
        titleCodeMap.clear()
        titleCodeMap["calculusandanalyticgeometry"] = "MTH104"
        titleCodeMap["datastructures"] = "CSC211"
        
        val titleCreditField = PortalRepository::class.java.getDeclaredField("courseTitleToCreditMap")
        titleCreditField.isAccessible = true
        val titleCreditMap = titleCreditField.get(repository) as MutableMap<String, String>
        titleCreditMap.clear()
        titleCreditMap["calculusandanalyticgeometry"] = "3"
        titleCreditMap["datastructures"] = "4"
        
        val latestSemesterField = PortalRepository::class.java.getDeclaredField("latestSemesterName")
        latestSemesterField.isAccessible = true
        latestSemesterField.set(repository, "Spring 2026")

        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>S#</td>
                        <td>Course Title</td>
                        <td>Class</td>
                        <td>Faculty</td>
                        <td>Lectures</td>
                        <td>P</td>
                        <td>A</td>
                        <td>Thy%</td>
                        <td>LAB%</td>
                    </tr>
                    <tr>
                        <td>1</td>
                        <td>Calculus and Analytic Geometry</td>
                        <td>BCS 3 C</td>
                        <td>Dr. Saeed Ullah Jan</td>
                        <td>17</td>
                        <td>16</td>
                        <td>1</td>
                        <td>94%</td>
                        <td>N/A</td>
                    </tr>
                    <tr>
                        <td>2</td>
                        <td>Data Structures</td>
                        <td>BCS 3 C</td>
                        <td>Qurat Ul Ain</td>
                        <td>27</td>
                        <td>22</td>
                        <td>5</td>
                        <td>94%</td>
                        <td>64%</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = invokeParseEnrolledCourses(html)
        assertNotNull(result)
        assertEquals("Spring 2026", result.semesterName)
        assertEquals(2, result.courses.size)

        val c1 = result.courses.find { it.courseTitle == "Calculus and Analytic Geometry" }
        assertNotNull(c1)
        assertEquals("MTH104", c1!!.courseCode)
        assertEquals("3", c1.creditHours)
        assertEquals("BCS 3 C", c1.section)
        assertEquals("Dr. Saeed Ullah Jan", c1.instructorName)

        val c2 = result.courses.find { it.courseTitle == "Data Structures" }
        assertNotNull(c2)
        assertEquals("CSC211", c2!!.courseCode)
        assertEquals("4", c2.creditHours)
        assertEquals("BCS 3 C", c2.section)
        assertEquals("Qurat Ul Ain", c2.instructorName)
    }
}
