package com.example.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PeopleOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DirectorateUser
import com.example.data.model.UserRole
import com.example.ui.components.GlassCircleIcon
import com.example.ui.components.LivingAtmosphericBackground
import com.example.ui.components.StatusGlassChip
import com.example.ui.components.complianceGlassCard
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.LocalCompliancePalette

@Composable
fun SystemUsersScreen(
    users: List<DirectorateUser>,
    currentUserRole: UserRole,
    onUpdateRole: (DirectorateUser, UserRole) -> Unit,
    onToggleStatus: (DirectorateUser) -> Unit,
    onUpdateEmail: (DirectorateUser, String) -> Unit,
    onForceResetPassword: (DirectorateUser) -> Unit,
    onProvisionUser: (email: String, password: String, name: String, staffNo: String, role: UserRole, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val p = LocalCompliancePalette.current
    val listState = rememberLazyListState()

    var search by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf("ALL") }
    var editingEmailUser by remember { mutableStateOf<DirectorateUser?>(null) }
    var showProvisionDialog by remember { mutableStateOf(false) }

    val showSummaryStrip by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 36
        }
    }

    val totalCount = users.size
    val suspendedCount = remember(users) {
        users.count { it.status.equals("suspended", true) || it.status.equals("inactive", true) }
    }
    val activeCount = (totalCount - suspendedCount).coerceAtLeast(0)
    val adminCount = remember(users) { users.count { it.role.isAdminOrAbove } }

    val filtered = remember(users, search, roleFilter) {
        val q = search.trim().lowercase()
        users.filter { u ->
            val roleOk = roleFilter == "ALL" || u.role.key == roleFilter
            val qOk = q.isBlank() ||
                u.displayName.lowercase().contains(q) ||
                u.email.lowercase().contains(q)
            roleOk && qOk
        }
    }

    LivingAtmosphericBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Sleek Glass Header + Provision Pill CTA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCircleIcon(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DIRECTORY & ACCESS",
                        color = p.textSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Directorate Users",
                        color = p.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (currentUserRole.isSuperAdmin) {
                    Row(
                        modifier = Modifier
                            .height(42.dp)
                            .shadow(12.dp, CircleShape, spotColor = p.tangerine.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable { showProvisionDialog = true }
                            .padding(horizontal = 14.dp)
                            .testTag("provision_user_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Provision",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. Smoothly Collapsible Directory KPI Strip (Auto-tucks when scrolling list)
            AnimatedVisibility(
                visible = showSummaryStrip,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DirectoryMetricTile(
                        label = "TOTAL",
                        value = totalCount.toString(),
                        accent = p.tangerine,
                        modifier = Modifier.weight(1f)
                    )
                    DirectoryMetricTile(
                        label = "ACTIVE",
                        value = activeCount.toString(),
                        accent = p.emerald,
                        modifier = Modifier.weight(1f)
                    )
                    DirectoryMetricTile(
                        label = if (suspendedCount > 0) "SUSPENDED" else "ADMINS",
                        value = if (suspendedCount > 0) suspendedCount.toString() else adminCount.toString(),
                        accent = if (suspendedCount > 0) p.rose else p.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Glass Search + Role Filter Pills
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassDirectorySearchBar(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Search officer name or email..."
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val roleOptions = listOf(
                        "ALL" to "All ($totalCount)",
                        "super_admin" to "Super Admin",
                        "admin" to "Admin",
                        "standard" to "Standard"
                    )
                    roleOptions.forEach { (key, label) ->
                        val selected = roleFilter == key
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier
                                            .background(Brush.horizontalGradient(listOf(p.tangerine, p.tangerineEnd)))
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                    } else {
                                        Modifier
                                            .background(p.trackFill)
                                            .border(1.dp, p.borderBottom, CircleShape)
                                    }
                                )
                                .clickable { roleFilter = key }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White else p.textPrimary,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 4. Well-Fitted 2-Tier Glass User Cards
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .complianceGlassCard(26.dp)
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(p.trackFill)
                                    .border(1.dp, p.borderTop, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PeopleOutline,
                                    contentDescription = null,
                                    tint = p.tangerine,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Text(
                                text = "No Matching Officers",
                                color = p.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Try clearing your search or switching the role filter above.",
                                color = p.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            if (search.isNotBlank() || roleFilter != "ALL") {
                                TextButton(
                                    onClick = {
                                        search = ""
                                        roleFilter = "ALL"
                                    }
                                ) {
                                    Text("Reset Filters", color = p.tangerine, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.userId }) { user ->
                        DirectorateUserGlassCard(
                            user = user,
                            currentUserRole = currentUserRole,
                            onUpdateRole = { role -> onUpdateRole(user, role) },
                            onEditEmail = { editingEmailUser = user },
                            onResetPassword = { onForceResetPassword(user) },
                            onToggleStatus = { onToggleStatus(user) }
                        )
                    }
                }
            }
        }
    }

    // Update Email Dialog
    editingEmailUser?.let { target ->
        var newEmail by remember(target.userId) { mutableStateOf(target.email) }
        val emailValid = newEmail.trim().contains("@") && newEmail.trim().contains(".")
        AlertDialog(
            onDismissRequest = { editingEmailUser = null },
            containerColor = if (p.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = p.textPrimary,
            textContentColor = p.textSecondary,
            title = {
                Text("Update Officer Email", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Updating login email for ${target.displayName}.",
                        fontSize = 13.sp,
                        color = p.textSecondary
                    )
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Institutional Email") },
                        singleLine = true,
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = emailValid && newEmail.trim() != target.email.trim(),
                    colors = ButtonDefaults.buttonColors(containerColor = p.tangerine, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        onUpdateEmail(target, newEmail.trim())
                        editingEmailUser = null
                    }
                ) {
                    Text("Update", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEmailUser = null }) {
                    Text("Cancel", color = p.textSecondary)
                }
            }
        )
    }

    // Provision Officer Dialog
    if (showProvisionDialog) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("12345678") }
        var passwordVisible by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("") }
        var staffNo by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(UserRole.STANDARD) }

        AlertDialog(
            onDismissRequest = { showProvisionDialog = false },
            containerColor = if (p.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = p.textPrimary,
            textContentColor = p.textSecondary,
            title = {
                Text("Provision Officer Account", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Institutional Email") },
                        singleLine = true,
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = staffNo,
                        onValueChange = { staffNo = it },
                        label = { Text("Staff Number") },
                        singleLine = true,
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Initial Password (min 6 chars)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = p.textSecondary
                                )
                            }
                        },
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "ASSIGNED ROLE",
                        color = p.textSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserRole.entries.forEach { r ->
                            val isSelected = role == r
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) p.tangerine else p.trackFill)
                                    .border(1.dp, if (isSelected) Color.White.copy(alpha = 0.3f) else p.borderBottom, CircleShape)
                                    .clickable { role = r }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = r.label,
                                    color = if (isSelected) Color.White else p.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = email.trim().contains("@") && password.length >= 6 && name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = p.tangerine, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        onProvisionUser(email.trim(), password, name.trim(), staffNo.trim(), role) {
                            showProvisionDialog = false
                        }
                    }
                ) {
                    Text("Create Account", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProvisionDialog = false }) {
                    Text("Cancel", color = p.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun DirectoryMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Column(
        modifier = modifier
            .complianceGlassCard(18.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = p.textSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = value,
            color = accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1
        )
    }
}

@Composable
private fun GlassDirectorySearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .complianceGlassCard(18.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = p.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = p.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = p.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(p.tangerine),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Clear search",
                tint = p.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

@Composable
private fun DirectorateUserGlassCard(
    user: DirectorateUser,
    currentUserRole: UserRole,
    onUpdateRole: (UserRole) -> Unit,
    onEditEmail: () -> Unit,
    onResetPassword: () -> Unit,
    onToggleStatus: () -> Unit
) {
    val p = LocalCompliancePalette.current
    var showRoleMenu by remember { mutableStateOf(false) }

    val isSuspended = user.status.equals("suspended", true) || user.status.equals("inactive", true)
    val canEditRole = !user.isOwner && currentUserRole.isSuperAdmin
    val canManageUser = !user.isOwner && currentUserRole.isAdminOrAbove

    val roleAccent = when {
        user.isOwner || user.role.isSuperAdmin -> p.tangerine
        user.role.isAdminOrAbove -> p.emerald
        else -> p.textSecondary
    }
    val initials = remember(user.displayName) {
        user.displayName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "U" }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .complianceGlassCard(24.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tier 1: Monogram Avatar + Identity + Status Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(roleAccent.copy(alpha = if (p.isDark) 0.20f else 0.14f))
                    .border(1.dp, roleAccent.copy(alpha = 0.50f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = if (roleAccent == p.textSecondary) p.textPrimary else roleAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = user.displayName.ifBlank { "Unnamed Officer" },
                        color = p.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (user.isOwner) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(p.tangerine.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "OWNER",
                                color = p.tangerine,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
                Text(
                    text = user.email,
                    color = p.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            StatusGlassChip(
                dotColor = if (isSuspended) p.rose else p.emerald,
                text = if (isSuspended) "SUSPENDED" else "ACTIVE"
            )
        }

        HorizontalDivider(
            thickness = 0.8.dp,
            color = p.borderBottom
        )

        // Tier 2: Fitted Action Bar (Role Pill on Left, Compact Admin Controls on Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(p.trackFill)
                        .border(1.dp, p.borderBottom, CircleShape)
                        .clickable(enabled = canEditRole) { showRoleMenu = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (user.isOwner) Icons.Rounded.Lock else Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = roleAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = user.role.label,
                        color = p.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (canEditRole) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Change role",
                            tint = p.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showRoleMenu,
                    onDismissRequest = { showRoleMenu = false }
                ) {
                    UserRole.entries.forEach { r ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = r.label,
                                    fontWeight = if (r == user.role) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showRoleMenu = false
                                if (r != user.role) onUpdateRole(r)
                            }
                        )
                    }
                }
            }

            if (canManageUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Edit Email
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(p.trackFill)
                            .border(1.dp, p.borderBottom, CircleShape)
                            .clickable(onClick = onEditEmail),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Email",
                            tint = p.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Reset Password
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(p.tangerine.copy(alpha = 0.14f))
                            .border(1.dp, p.tangerine.copy(alpha = 0.32f), CircleShape)
                            .clickable(onClick = onResetPassword),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = "Reset Password",
                            tint = p.tangerine,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Activate / Suspend Pill
                    val actionColor = if (isSuspended) p.emerald else p.rose
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(actionColor.copy(alpha = 0.14f))
                            .border(1.dp, actionColor.copy(alpha = 0.35f), CircleShape)
                            .clickable(onClick = onToggleStatus)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuspended) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                            contentDescription = null,
                            tint = actionColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isSuspended) "Activate" else "Suspend",
                            color = actionColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}