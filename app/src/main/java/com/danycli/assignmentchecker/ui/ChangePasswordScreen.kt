package com.danycli.assignmentchecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    fetchRules: suspend () -> String,
    onSubmit: suspend (current: String, new: String, confirm: String) -> Result<String>,
    onPasswordUpdated: (String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var currentPassVisible by remember { mutableStateOf(false) }
    var newPassVisible by remember { mutableStateOf(false) }
    var confirmPassVisible by remember { mutableStateOf(false) }

    var dynamicRules by remember { mutableStateOf("Loading password requirements...") }
    var isCheckingRules by remember { mutableStateOf(true) }

    var isSubmitting by remember { mutableStateOf(false) }
    var generalError by remember { mutableStateOf<String?>(null) }

    // Field Validation States
    var currentPasswordError by remember { mutableStateOf<String?>(null) }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        dynamicRules = fetchRules()
        isCheckingRules = false
    }

    // Local Validation logic
    fun validateFields(): Boolean {
        var isValid = true

        if (currentPassword.isBlank()) {
            currentPasswordError = "Current password is required"
            isValid = false
        } else {
            currentPasswordError = null
        }

        val hasUpper = newPassword.any { it.isUpperCase() }
        val hasLower = newPassword.any { it.isLowerCase() }
        val hasDigit = newPassword.any { it.isDigit() }
        val hasSpecial = newPassword.any { !it.isLetterOrDigit() }

        if (newPassword.isBlank()) {
            newPasswordError = "New password is required"
            isValid = false
        } else if (newPassword.length < 8) {
            newPasswordError = "Password must be at least 8 characters long"
            isValid = false
        } else if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            newPasswordError = "Password must contain uppercase, lowercase, numbers, and special characters"
            isValid = false
        } else {
            newPasswordError = null
        }

        if (confirmPassword.isBlank()) {
            confirmPasswordError = "Please confirm your new password"
            isValid = false
        } else if (confirmPassword != newPassword) {
            confirmPasswordError = "New passwords do not match"
            isValid = false
        } else {
            confirmPasswordError = null
        }

        return isValid
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Change Password", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSubmitting) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Password Requirements Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Password Requirements",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isCheckingRules) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                dynamicRules,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Input Fields Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Current Password
                        Column {
                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = {
                                    currentPassword = it
                                    if (currentPasswordError != null) validateFields()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Current Password") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { currentPassVisible = !currentPassVisible },
                                        enabled = !isSubmitting
                                    ) {
                                        Icon(
                                            imageVector = if (currentPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (currentPassVisible) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                visualTransformation = if (currentPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    autoCorrect = false,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                isError = currentPasswordError != null,
                                enabled = !isSubmitting
                            )
                            if (currentPasswordError != null) {
                                Text(
                                    text = currentPasswordError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }

                        // New Password
                        Column {
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = {
                                    newPassword = it
                                    if (newPasswordError != null) validateFields()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("New Password") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { newPassVisible = !newPassVisible },
                                        enabled = !isSubmitting
                                    ) {
                                        Icon(
                                            imageVector = if (newPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (newPassVisible) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                visualTransformation = if (newPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    autoCorrect = false,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                isError = newPasswordError != null,
                                enabled = !isSubmitting
                            )
                            if (newPasswordError != null) {
                                Text(
                                    text = newPasswordError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }

                        // Confirm New Password
                        Column {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    if (confirmPasswordError != null) validateFields()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Confirm New Password") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { confirmPassVisible = !confirmPassVisible },
                                        enabled = !isSubmitting
                                    ) {
                                        Icon(
                                            imageVector = if (confirmPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (confirmPassVisible) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                visualTransformation = if (confirmPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    autoCorrect = false,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                isError = confirmPasswordError != null,
                                enabled = !isSubmitting
                            )
                            if (confirmPasswordError != null) {
                                Text(
                                    text = confirmPasswordError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // General Status/Error Panel
            if (generalError != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = generalError.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // 3. Submit Button
            item {
                Button(
                    onClick = {
                        if (validateFields()) {
                            isSubmitting = true
                            generalError = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Updating Password...")
                    } else {
                        Text("Change Password")
                    }
                }
            }
        }
    }

    // Trigger Network Submission
    if (isSubmitting && generalError == null) {
        LaunchedEffect(isSubmitting) {
            val result = onSubmit(currentPassword, newPassword, confirmPassword)
            if (result.isSuccess) {
                onPasswordUpdated(result.getOrThrow())
            } else {
                generalError = result.exceptionOrNull()?.message ?: "An unexpected error occurred"
                isSubmitting = false
            }
        }
    }
}
