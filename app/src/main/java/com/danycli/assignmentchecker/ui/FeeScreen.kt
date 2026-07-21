package com.danycli.assignmentchecker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.FeeChallan
import com.danycli.assignmentchecker.FeeLedgerEntry
import com.danycli.assignmentchecker.FeeSnapshot
import com.danycli.assignmentchecker.FeeHistorySectionRecord
import com.danycli.assignmentchecker.ScholarshipRecord
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeeScreen(
    feeSnapshot: FeeSnapshot?,
    isRefreshing: Boolean,
    onRefreshFee: suspend () -> FeeSnapshot,
    onDownloadChallan: (FeeChallan) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showMessage = LocalShowMessage.current
    var isLoading by remember { mutableStateOf(false) }

    var isSemesterFeesExpanded by remember { mutableStateOf(false) }
    var isBoardingFeesExpanded by remember { mutableStateOf(false) }
    var isMiscChargesExpanded by remember { mutableStateOf(false) }
    var isScholarshipsExpanded by remember { mutableStateOf(false) }
    var isActivityLogExpanded by remember { mutableStateOf(false) }

    val currencyFormatter = remember { DecimalFormat("#,###") }

    fun formatAmount(amount: Double): String {
        return "PKR ${currencyFormatter.format(amount)}"
    }

    LaunchedEffect(feeSnapshot) {
        if (feeSnapshot == null || feeSnapshot.outstandingBalance == null || feeSnapshot.ledger == null || feeSnapshot.semesterFees == null) {
            scope.launch {
                isLoading = true
                try {
                    onRefreshFee()
                } catch (e: Exception) {
                    showMessage(e.message ?: "Failed to refresh fee details")
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Fee", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing || isLoading,
                onRefresh = {
                    scope.launch {
                        try {
                            onRefreshFee()
                        } catch (e: Exception) {
                            showMessage(e.message ?: "Failed to refresh fee details")
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (feeSnapshot == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Fetching billing details...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item {
                            FinancialSummaryCard(
                                snapshot = feeSnapshot,
                                formatAmount = ::formatAmount
                            )
                        }

                        val challanList = feeSnapshot.challans
                        val unpaidChallans = challanList?.filter { it.status == "Unpaid" } ?: emptyList()
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Pending Challans",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                if (unpaidChallans.isNotEmpty()) {
                                    unpaidChallans.forEach { challan ->
                                        ChallanCard(
                                            challan = challan,
                                            formatAmount = ::formatAmount,
                                            onDownload = { onDownloadChallan(challan) }
                                        )
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "No voucher at the moment",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val semesterFeesList = feeSnapshot.semesterFees ?: emptyList()
                        item {
                            val semesterSum = semesterFeesList.sumOf { it.semesterDues }
                            val summary = "${semesterFeesList.size} Records • ${formatAmount(semesterSum)}"
                            CollapsibleCategoryCard(
                                title = "Semester Fees",
                                summaryText = summary,
                                isExpanded = isSemesterFeesExpanded,
                                onToggle = { isSemesterFeesExpanded = !isSemesterFeesExpanded }
                            ) {
                                if (semesterFeesList.isEmpty()) {
                                    Text("No semester fee records found.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    semesterFeesList.forEach { record ->
                                        SemesterFeeRecordCard(record = record, formatAmount = ::formatAmount)
                                    }
                                }
                            }
                        }

                        val boardingFeesList = feeSnapshot.boardingFees ?: emptyList()
                        item {
                            val boardingSum = boardingFeesList.sumOf { it.semesterDues }
                            val summary = "${boardingFeesList.size} Records • ${formatAmount(boardingSum)}"
                            CollapsibleCategoryCard(
                                title = "Boarding Fees",
                                summaryText = summary,
                                isExpanded = isBoardingFeesExpanded,
                                onToggle = { isBoardingFeesExpanded = !isBoardingFeesExpanded }
                            ) {
                                if (boardingFeesList.isEmpty()) {
                                    Text("No boarding fee records found.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    boardingFeesList.forEach { record ->
                                        BoardingFeeRecordCard(record = record, formatAmount = ::formatAmount)
                                    }
                                }
                            }
                        }

                        val miscChargesList = feeSnapshot.miscCharges ?: emptyList()
                        item {
                            val miscSum = miscChargesList.sumOf { it.semesterDues }
                            val summary = "${miscChargesList.size} Records • ${formatAmount(miscSum)}"
                            CollapsibleCategoryCard(
                                title = "Miscellaneous Charges",
                                summaryText = summary,
                                isExpanded = isMiscChargesExpanded,
                                onToggle = { isMiscChargesExpanded = !isMiscChargesExpanded }
                            ) {
                                if (miscChargesList.isEmpty()) {
                                    Text("No miscellaneous charges found.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    miscChargesList.forEach { record ->
                                        MiscChargeRecordCard(record = record, formatAmount = ::formatAmount)
                                    }
                                }
                            }
                        }

                        val scholarshipsList = feeSnapshot.scholarships ?: emptyList()
                        item {
                            val scholarshipSum = scholarshipsList.sumOf { it.amount }
                            val summary = "${scholarshipsList.size} Awards • ${formatAmount(scholarshipSum)}"
                            CollapsibleCategoryCard(
                                title = "Scholarship & Financial Assistance",
                                summaryText = summary,
                                isExpanded = isScholarshipsExpanded,
                                onToggle = { isScholarshipsExpanded = !isScholarshipsExpanded }
                            ) {
                                if (scholarshipsList.isEmpty()) {
                                    Text("No scholarship or financial assistance awards found.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    scholarshipsList.forEach { record ->
                                        ScholarshipRecordCard(record = record, formatAmount = ::formatAmount)
                                    }
                                }
                            }
                        }


                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleCategoryCard(
    title: String,
    summaryText: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = summaryText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun SemesterFeeRecordCard(
    record: FeeHistorySectionRecord,
    formatAmount: (Double) -> String
) {
    val isDark = isSystemInDarkTheme()
    val isPaid = record.outstandingBalance <= 0.0
    val statusBg = if (isPaid) Color(0x222E7D32) else Color(0x33E65100)
    val statusTextColor = if (isPaid) {
        if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    } else {
        if (isDark) Color(0xFFFFB74D) else Color(0xFFFF8F00)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = record.session,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = record.feeType,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isPaid) "Paid" else "Pending",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Billed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(record.semesterDues), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                val scholarshipAmt = if (record.assistancePaid > 0.0) {
                    record.assistancePaid
                } else if (record.assistance > 0.0) {
                    record.assistance
                } else {
                    0.0
                }
                if (scholarshipAmt > 0.0) {
                    Column {
                        Text("Scholarship", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatAmount(scholarshipAmt), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32))
                    }
                }
                Column {
                    Text("Paid", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(record.duesPaid), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Outstanding", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val outstanding = record.outstandingBalance
                    if (outstanding < 0.0) {
                        Text("PKR 0", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32))
                    } else {
                        Text(formatAmount(outstanding), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isPaid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (record.outstandingBalance < 0.0) {
                Text(
                    text = "Credit Surplus: ${formatAmount(kotlin.math.abs(record.outstandingBalance))}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun BoardingFeeRecordCard(
    record: FeeHistorySectionRecord,
    formatAmount: (Double) -> String
) {
    val isDark = isSystemInDarkTheme()
    val isPaid = record.outstandingBalance <= 0.0
    val statusBg = if (isPaid) Color(0x222E7D32) else Color(0x33E65100)
    val statusTextColor = if (isPaid) {
        if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    } else {
        if (isDark) Color(0xFFFFB74D) else Color(0xFFFF8F00)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.session,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isPaid) "Paid" else "Pending",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Billed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(record.semesterDues), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text("Paid", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(record.duesPaid), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Outstanding", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(record.outstandingBalance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isPaid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun MiscChargeRecordCard(
    record: FeeHistorySectionRecord,
    formatAmount: (Double) -> String
) {
    val isDark = isSystemInDarkTheme()
    val isPaid = record.outstandingBalance <= 0.0
    val statusBg = if (isPaid) Color(0x222E7D32) else Color(0x33E65100)
    val statusTextColor = if (isPaid) {
        if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    } else {
        if (isDark) Color(0xFFFFB74D) else Color(0xFFFF8F00)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = record.session,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = record.feeType,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isPaid) "Paid" else "Pending",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Billed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(record.semesterDues), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text("Paid", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(record.duesPaid), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Outstanding", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(record.outstandingBalance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isPaid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ScholarshipRecordCard(
    record: ScholarshipRecord,
    formatAmount: (Double) -> String
) {
    val isDark = isSystemInDarkTheme()
    val awardTextColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val awardBgColor = if (isDark) Color(0xFF132719) else Color(0xFFE8F5E9)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = record.session,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(awardBgColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (record.type == "Scholarship Awarded") "Awarded" else "Adjustment",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = awardTextColor
                        )
                    }
                }
                Text(
                    text = record.feeType,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatAmount(record.amount),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = awardTextColor
            )
        }
    }
}

@Composable
fun FinancialSummaryCard(
    snapshot: FeeSnapshot,
    formatAmount: (Double) -> String
) {
    val isDark = isSystemInDarkTheme()
    
    val unpaidChallans = snapshot.challans?.filter { it.status == "Unpaid" } ?: emptyList()
    val isAnyOverdue = unpaidChallans.any { isChallanOverdue(it.dueDate) }
    
    val statusText = when {
        snapshot.outstandingBalance == null -> "Status N/A"
        snapshot.outstandingBalance <= 0.0 -> "All Dues Cleared"
        isAnyOverdue -> "Overdue Dues"
        else -> "Pending Payment"
    }

    val (statusBg, statusTextColor) = when {
        snapshot.outstandingBalance == null -> Pair(Color(0x22FFFFFF), Color(0xFFE5EAF0))
        snapshot.outstandingBalance <= 0.0 -> Pair(Color(0x222E7D32), Color(0xFF81C784))
        isAnyOverdue -> Pair(Color(0x33C62828), Color(0xFFEF5350))
        else -> Pair(Color(0x33E65100), Color(0xFFFFB74D))
    }

    val cardGradient = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Color(0xFF1E3A34), Color(0xFF122420))
        } else {
            listOf(Color(0xFF004643), Color(0xFF002927))
        }
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(cardGradient)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACCOUNT SUMMARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 1.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusTextColor
                        )
                    }
                }

                Column {
                    Text(
                        text = "Outstanding Balance",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (snapshot.outstandingBalance != null) {
                        val outstanding = snapshot.outstandingBalance
                        if (outstanding > 0.0) {
                            Text(
                                text = formatAmount(outstanding),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Column {
                                Text(
                                    text = "PKR 0",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (outstanding < 0.0) {
                                    val creditAmt = kotlin.math.abs(outstanding)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Scholarship Credit: ${formatAmount(creditAmt)}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF81C784)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No outstanding balance found",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                val totalScholarship = snapshot.scholarships?.sumOf { it.amount } ?: 0.0
                val totalPaidCash = snapshot.totalCredits?.let { it - totalScholarship }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.05f)) {
                        Text(
                            text = "TOTAL BILLED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = snapshot.totalDebits?.let { formatAmount(it) } ?: "N/A",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SCHOLARSHIP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatAmount(totalScholarship),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81C784)
                        )
                    }

                    Column(modifier = Modifier.weight(1.05f), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "TOTAL PAID",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = totalPaidCash?.let { formatAmount(it) } ?: "N/A",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                snapshot.lastTransactionDate?.let { lastDate ->
                    if (lastDate.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Last Transaction: $lastDate",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChallanCard(
    challan: FeeChallan,
    formatAmount: (Double) -> String,
    onDownload: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val isOverdue = isChallanOverdue(challan.dueDate)
    
    val cardColor = if (isDark) {
        if (isOverdue) Color(0xFF2C1C1E) else Color(0xFF1F242A)
    } else {
        if (isOverdue) Color(0xFFFFF5F5) else Color(0xFFFFFBF0)
    }
    
    val borderColor = if (isDark) {
        if (isOverdue) Color(0xFFCF6679).copy(alpha = 0.4f) else Color.Transparent
    } else {
        if (isOverdue) Color(0xFFF8B4B4) else Color(0xFFFFE0B2).copy(alpha = 0.5f)
    }
    
    val badgeBgColor = if (isOverdue) Color(0x33C62828) else Color(0x33E65100)
    val badgeTextColor = if (isOverdue) Color(0xFFEF5350) else Color(0xFFFFB74D)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape = RoundedCornerShape(16.dp))
            .border(
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = challan.semester,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBgColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isOverdue) "Overdue" else "Unpaid",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                    }
                }
                
                Text(
                    text = "Challan ID: ${challan.challanId}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = if (isOverdue) Color(0xFFEF5350) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Due: ${challan.dueDate}",
                        fontSize = 12.sp,
                        fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOverdue) Color(0xFFEF5350) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (challan.amount > 0.0) {
                    Text(
                        text = formatAmount(challan.amount),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverdue) Color(0xFFEF5350) else MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "View PDF",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Challan",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLedgerCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "There are no transaction records available on your student portal ledger.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LedgerTimeline(
    entries: List<FeeLedgerEntry>,
    formatAmount: (Double) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Timeline Graphics
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(24.dp)
                ) {
                    val isCredit = entry.type == "Credit"
                    // Dot
                    val nodeColor = if (isCredit) {
                        Color(0xFF2E7D32)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(nodeColor)
                    )
                    // Line
                    if (index < entries.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(56.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                        )
                    }
                }

                // Right Transaction Details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = entry.description,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        val isCredit = entry.type == "Credit"
                        val displayAmount = if (isCredit) {
                            "+${formatAmount(entry.amount)}"
                        } else {
                            "-${formatAmount(entry.amount)}"
                        }
                        val amountColor = if (isCredit) {
                            if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
                        } else {
                            if (isSystemInDarkTheme()) Color(0xFFCF6679) else Color(0xFFB00020)
                        }
                        
                        Text(
                            text = displayAmount,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = amountColor
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.date,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val isCredit = entry.type == "Credit"
                        val isScholarship = isCredit && (
                            entry.description.contains("scholarship", ignoreCase = true) ||
                            entry.description.contains("assistance", ignoreCase = true)
                        )
                        val badgeTextStr = when {
                            isScholarship -> "SCHOLARSHIP"
                            isCredit -> "PAYMENT"
                            else -> "CHARGE"
                        }
                        val badgeBg = if (isCredit) {
                            if (isSystemInDarkTheme()) Color(0xFF1A3922) else Color(0xFFE8F5E9)
                        } else {
                            if (isSystemInDarkTheme()) Color(0xFF401D24) else Color(0xFFFFEBEE)
                        }
                        val badgeText = if (isCredit) {
                            if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
                        } else {
                            if (isSystemInDarkTheme()) Color(0xFFCF6679) else Color(0xFFB00020)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeBg)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeTextStr,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isChallanOverdue(dueDateStr: String): Boolean {
    if (dueDateStr.isBlank()) return false
    val cleanDate = dueDateStr.trim()
    val formats = listOf(
        "dd MMM yyyy",
        "dd-MMM-yyyy",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "dd MMM yy",
        "dd-MMM-yy"
    )
    val now = Date()
    for (format in formats) {
        try {
            val sdf = SimpleDateFormat(format, Locale.US)
            val date = sdf.parse(cleanDate)
            if (date != null && date.before(now)) {
                val todaySdf = SimpleDateFormat("yyyyMMdd", Locale.US)
                if (todaySdf.format(date) == todaySdf.format(now)) {
                    return false
                }
                return true
            }
        } catch (e: Exception) {
            // try next format
        }
    }
    return false
}
