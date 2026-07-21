package com.danycli.assignmentchecker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.assignmentchecker.StudentProfile
import com.danycli.assignmentchecker.isNetworkError
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: StudentProfile?,
    photoBytes: ByteArray?,
    isRefreshing: Boolean,
    completedSemesters: Int,
    onRefreshProfile: suspend () -> Pair<StudentProfile, ByteArray?>,
    onBack: () -> Unit
) {
    var currentProfile by remember { mutableStateOf(profile) }
    var currentPhotoBytes by remember { mutableStateOf(photoBytes) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val showMessage = LocalShowMessage.current

    val copyToClipboard = { label: String, value: String ->
        if (value.isNotBlank()) {
            clipboardManager.setText(AnnotatedString(value))
            showMessage("$label copied to clipboard")
        }
    }

    // Update internal state when parameters change
    LaunchedEffect(profile, photoBytes) {
        if (profile != null) {
            currentProfile = profile
        }
        if (photoBytes != null) {
            currentPhotoBytes = photoBytes
        }
    }

    // Trigger auto-fetch if no profile is loaded
    LaunchedEffect(currentProfile) {
        if (currentProfile == null) {
            isLoading = true
            errorMsg = null
            scope.launch {
                try {
                    val result = onRefreshProfile()
                    currentProfile = result.first
                    currentPhotoBytes = result.second
                } catch (e: Exception) {
                    errorMsg = if (isNetworkError(e)) {
                        "Network unavailable. Please check your connection to load profile details."
                    } else {
                        e.message ?: "Failed to load student profile"
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val profileBitmap = remember(currentPhotoBytes) {
        currentPhotoBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Student Profile",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing || isLoading,
                onRefresh = {
                    isLoading = true
                    errorMsg = null
                    scope.launch {
                        try {
                            val result = onRefreshProfile()
                            currentProfile = result.first
                            currentPhotoBytes = result.second
                            showMessage("Profile updated successfully")
                        } catch (e: Exception) {
                            val msg = if (isNetworkError(e)) {
                                "Network unavailable. Please check your connection to update profile."
                            } else {
                                e.message ?: "Failed to refresh profile"
                            }
                            if (currentProfile == null) {
                                errorMsg = msg
                            } else {
                                showMessage(msg)
                            }
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                if (currentProfile == null && errorMsg != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Error icon",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = errorMsg ?: "Unknown error occurred",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    isLoading = true
                                    errorMsg = null
                                    scope.launch {
                                        try {
                                            val result = onRefreshProfile()
                                            currentProfile = result.first
                                            currentPhotoBytes = result.second
                                        } catch (e: Exception) {
                                            errorMsg = if (isNetworkError(e)) {
                                                "Network unavailable. Please check your connection to load profile details."
                                            } else {
                                                e.message ?: "Failed to load student profile"
                                            }
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                } else if (currentProfile != null) {
                    val p = currentProfile!!
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        // 1. Profile Hero Header
                        item {
                            ProfileHeroHeader(
                                name = p.name,
                                regNumber = p.regNumber,
                                program = p.program,
                                section = p.section,
                                campus = p.campus,
                                scholarshipStatus = p.scholarshipStatus,
                                bitmap = profileBitmap
                            )
                        }

                        // 2. Academic Details Card
                        val hasAcademic = p.program.isNotBlank() || p.regNumber.isNotBlank() || p.campus.isNotBlank() || p.section.isNotBlank() || completedSemesters >= 0
                        if (hasAcademic) {
                            item {
                                DetailsCard(title = "Academic Details", icon = Icons.Outlined.Badge) {
                                    InfoRow(
                                        icon = Icons.Default.Class,
                                        label = "Program",
                                        value = p.program
                                    )
                                    InfoRow(
                                        icon = Icons.Default.Domain,
                                        label = "Campus",
                                        value = p.campus
                                    )
                                    InfoRow(
                                        icon = Icons.Default.MeetingRoom,
                                        label = "Section",
                                        value = p.section
                                    )
                                    val currentSemester = completedSemesters + 1
                                    if (completedSemesters >= 0) {
                                        InfoRow(
                                            icon = Icons.Default.School,
                                            label = "Current Semester",
                                            value = com.danycli.assignmentchecker.getOrdinalSuffix(currentSemester)
                                        )
                                    }
                                    InfoRow(
                                        icon = Icons.Default.Badge,
                                        label = "Registration No",
                                        value = p.regNumber,
                                        isCopyable = true,
                                        onCopy = { copyToClipboard("Registration number", p.regNumber) }
                                    )
                                }
                            }
                        }

                        // 3. Personal Details Card
                        val hasPersonal = p.fatherName.isNotBlank() || p.dob.isNotBlank()
                        if (hasPersonal) {
                            item {
                                DetailsCard(title = "Personal Details", icon = Icons.Outlined.PersonOutline) {
                                    if (p.fatherName.isNotBlank()) {
                                        InfoRow(
                                            icon = Icons.Default.Person,
                                            label = "Father's Name",
                                            value = p.fatherName
                                        )
                                    }
                                    if (p.dob.isNotBlank()) {
                                        InfoRow(
                                            icon = Icons.Default.Cake,
                                            label = "Date of Birth",
                                            value = p.dob
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Contact Details Card
                        val hasContact = p.email.isNotBlank() || p.mobile.isNotBlank() || p.phone.isNotBlank()
                        if (hasContact) {
                            item {
                                DetailsCard(title = "Contact Details", icon = Icons.Outlined.ContactPhone) {
                                    if (p.email.isNotBlank()) {
                                        InfoRow(
                                            icon = Icons.Default.Email,
                                            label = "Email",
                                            value = p.email,
                                            isCopyable = true,
                                            onCopy = { copyToClipboard("Email address", p.email) }
                                        )
                                    }
                                    if (p.mobile.isNotBlank()) {
                                        InfoRow(
                                            icon = Icons.Default.PhoneAndroid,
                                            label = "Mobile Number",
                                            value = p.mobile,
                                            isCopyable = true,
                                            onCopy = { copyToClipboard("Mobile number", p.mobile) }
                                        )
                                    }
                                    if (p.phone.isNotBlank()) {
                                        InfoRow(
                                            icon = Icons.Default.Phone,
                                            label = "Phone Number",
                                            value = p.phone,
                                            isCopyable = true,
                                            onCopy = { copyToClipboard("Phone number", p.phone) }
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Address Details Card
                        val hasAddress = p.address.isNotBlank()
                        if (hasAddress) {
                            item {
                                DetailsCard(title = "Address Details", icon = Icons.Outlined.LocationOn) {
                                    InfoRow(
                                        icon = Icons.Default.Home,
                                        label = "Address",
                                        value = p.address
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeroHeader(
    name: String,
    regNumber: String,
    program: String,
    section: String,
    campus: String,
    scholarshipStatus: String,
    bitmap: Bitmap?
) {
    val isDark = isSystemInDarkTheme()
    val gradientColors = if (isDark) {
        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
    } else {
        listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surface
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(gradientColors))
        ) {
            Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-20).dp)
                    .size(160.dp)
                    .alpha(if (isDark) 0.03f else 0.08f),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Row: Avatar and Scholarship Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .shadow(8.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .border(4.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Profile Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val initials = getStudentInitials(name)
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    val isScholarship = scholarshipStatus.let { status ->
                        val s = status.trim().lowercase()
                        s.isNotBlank() && s != "none" && s != "no" && s != "n/a" &&
                        !s.contains("no scholarship") && !s.contains("no active") &&
                        (s.contains("yes") || s.contains("active") || s.contains("holder") || s.contains("eligible") || s.contains("approved"))
                    }
                    if (isScholarship) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF81C784).copy(alpha = 0.2f), Color(0xFF4CAF50).copy(alpha = 0.2f))))
                                .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EmojiEvents,
                                contentDescription = "Scholarship",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Scholarship",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                }

                // Middle: Name and Reg Number
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = name.trim(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = regNumber.trim(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Bottom: Information Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (program.isNotBlank()) {
                        InfoChip(icon = Icons.Outlined.School, text = program.trim())
                    }
                    if (campus.isNotBlank()) {
                        InfoChip(icon = Icons.Outlined.Domain, text = campus.trim())
                    }
                    if (section.isNotBlank()) {
                        InfoChip(icon = Icons.Outlined.Class, text = "Sec ${section.trim()}")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DetailsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            Column(content = content)
        }
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    isCopyable: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    if (value.isBlank()) return
    
    var showCopied by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        if (isCopyable) {
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = {
                    onCopy?.invoke()
                    showCopied = true
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (showCopied) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (showCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    tint = if (showCopied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            LaunchedEffect(showCopied) {
                if (showCopied) {
                    kotlinx.coroutines.delay(2000)
                    showCopied = false
                }
            }
        }
    }
}

private fun getStudentInitials(name: String?): String {
    val resolved = name?.trim()?.uppercase() ?: return "?"
    val parts = resolved.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    if (parts.size == 1) return parts[0].take(2)
    val firstInitial = parts[0].first()
    val lastInitial = parts.last().first()
    return "$firstInitial$lastInitial"
}
