# API Debug Guide for Historical Assignments

## Problem
Historical assignments are not showing up even though you have 2 submitted assignments.

## What Changed in the Code
1. ✅ Fetches historical assignments on LOGIN (not just when user clicks)
2. ✅ Summary card now shows:
   - Total = Pending + Submitted
   - Pending = Only unsubmitted assignments
   - Submitted = Graded/submitted assignments
3. ✅ Refresh button fetches both pending AND historical

## How to Debug

### Step 1: Check Logcat for Portal Responses
Run these adb commands in terminal:

```bash
# Clear old logs
adb logcat -c

# Start logging PortalRepository debug messages
adb logcat | grep "PortalAuth\|PortalRepository"
```

Then login in the app and check the output. Look for:
- `fetchHistoricalAssignments()` being called
- HTML response from `CoursePortalSubmittedAssignments.aspx`
- Whether assignments are being parsed

### Step 2: Check the API Endpoint
The app calls: `https://sis.cuiatd.edu.pk/CoursePortalSubmittedAssignments.aspx`

**Question:** Does this endpoint exist and return submitted assignments?

Open your browser and go to:
1. Login at: https://sis.cuiatd.edu.pk/Login.aspx
2. After login, navigate to: https://sis.cuiatd.edu.pk/CoursePortalSubmittedAssignments.aspx
3. Check if you see your submitted assignments there

### Step 3: Alternative Endpoints to Try
If `CoursePortalSubmittedAssignments.aspx` doesn't show submitted assignments, the endpoint name might be different:

```
Possible alternatives:
- /CoursePortalCompletedAssignments.aspx
- /CoursePortalGradedAssignments.aspx
- /CoursePortalAwaitingGradeAssignments.aspx
- /CoursePortalSubmissions.aspx
- /SubmittedAssignments.aspx
```

### Step 4: Check HTML Structure
If the endpoint exists, check the HTML structure of the page:
1. Open the submitted assignments page in browser
2. Right-click → Inspect
3. Look for the `<table>` element
4. Count the columns and note their order
5. Compare with what `fetchHistoricalAssignments()` expects:
   ```
   cols[1] = Course Title
   cols[2] = Assignment Title
   cols[3] = Deadline
   cols[4] = Submission Date
   cols[5] = Marks/Grade
   cols[6] = Download Link
   ```

### Step 5: Update PortalRepository if Needed
If the endpoint exists but has different columns, edit `PortalRepository.kt` line ~364:

```kotlin
fun fetchHistoricalAssignments(): List<Assignment> {
    // Change this URL if the endpoint is different
    val historicalUrl = "$baseUrl/CoursePortalSubmittedAssignments.aspx"
    
    // Adjust column indices if table structure is different
    // Current expectation: 7+ columns
    if (cols.size >= 7) {
        val course = cols[X].text().trim()      // Find correct column
        val title = cols[Y].text().trim()
        val deadline = cols[Z].text().trim()
        // ... etc
    }
}
```

## Next Steps

1. **If the endpoint doesn't exist:** The portal may not have a page for submitted assignments. In this case, we might need to:
   - Track submission status in local storage
   - Modify the parsing of pending assignments to detect submitted ones
   - Contact portal admin for API documentation

2. **If the endpoint exists but data is wrong:** Update the column indices in `fetchHistoricalAssignments()`

3. **If everything works:** Your 2 submitted assignments should now appear in the "Submitted Assignments" view, and the summary card should show:
   - Total: 3
   - Pending: 1
   - Submitted: 2

## Code Changes Made

**MainActivity.kt:**
- Line 103-107: Fetch historical on login
- Line 130: Pass historicalAssignments to AssignmentsList
- Line 134-140: Refresh also fetches historical
- Line 548: Total = pending + historical
- Line 556: Submitted = historical.size

**AssignmentsList function:** Now receives historicalAssignments parameter to calculate correct counts
