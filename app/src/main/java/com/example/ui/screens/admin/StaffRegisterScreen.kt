package com.example.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ComplianceReport
import com.example.data.model.SeverityLevel
import com.example.data.model.StaffMember
import com.example.ui.components.AtmosphericGlassBackground
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.MicroLabel
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SearchInputBar
import com.example.ui.components.SeverityBadge
import com.example.ui.components.SubScreenTopRow
import com.example.ui.components.glassFieldColors
import com.example.ui.theme.ComplianceTheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffRegisterScreen(
    staffList: List<StaffMember>,
    reports: List<ComplianceReport>,
    onSaveStaff: (StaffMember, Boolean, () -> Unit) -> Unit,
    onToggleArchive: (StaffMember) -> Unit,
    onReassignConflict: (StaffMember, String, StaffMember, Boolean, () -> Unit) -> Unit,
    onArchiveConflict: (StaffMember, StaffMember, Boolean, () -> Unit) -> Unit,
    onSelectReport: (ComplianceReport) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val ext = ComplianceTheme.extended
    var showArchived by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var editingStaff by remember { mutableStateOf<StaffMember?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedDossierStaff by remember { mutableStateOf<StaffMember?>(null) }

    var conflictHolder by remember { mutableStateOf<StaffMember?>(null) }
    var pendingSavePair by remember { mutableStateOf<Pair<StaffMember, Boolean>?>(null) }

    val filtered = remember(staffList, showArchived, search) {
        val q = search.trim().lowercase()
        staffList.filter { s ->
            val statusOk = if (showArchived) !s.isActive else s.isActive
            val qOk = q.isBlank() || s.name.lowercase().contains(q) || s.staffCode.lowercase().contains(q) ||
                s.department.lowercase().contains(q) || s.assignedLocation.lowercase().contains(q)
            statusOk && qOk
        }
    }

    AtmosphericGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            SubScreenTopRow(
                title = "Staff Register",
                subtitle = "${staffList.count { it.isActive }} active personnel",
                onBack = onBack,
                trailing = {
                    Surface(
                        onClick = {
                            editingStaff = null
                            showAddEditDialog = true
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_staff_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(" Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchInputBar(value = search, onValueChange = { search = it }, placeholder = "Search name, code, or wing...")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Active Staff", true to "Archived").forEach { (arch, label) ->
                        val selected = showArchived == arch
                        Surface(
                            onClick = { showArchived = arch },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else ext.glassBottom,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else ext.glassRimEnd)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        EmptyStatePanel(
                            title = "No Staff Found",
                            subtitle = if (showArchived) "No archived staff records." else "Add a staff member or clear your search filter."
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { person ->
                        val staffExceptions = reports.filter {
                            (person.id.isNotBlank() && it.staffId == person.id) ||
                                (person.staffCode.isNotBlank() && it.staffCode.equals(person.staffCode, true)) ||
                                it.staffName.equals(person.name, true)
                        }
                        val severity = SeverityLevel.fromCount(staffExceptions.size)
                        GlassCard(
                            onClick = { selectedDossierStaff = person },
                            cornerRadius = 22.dp,
                            contentPadding = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MonoCodeText(person.staffCode.ifBlank { "NO-CODE" })
                                        MicroLabel(person.department)
                                    }
                                    Text(
                                        text = person.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${person.role} • ${person.assignedLocation.ifBlank { "General Campus" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                SeverityBadge(severity)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = {
                                    editingStaff = person
                                    showAddEditDialog = true
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Staff",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { onToggleArchive(person) }) {
                                    Icon(
                                        if (person.isActive) Icons.Default.Archive else Icons.Default.RestoreFromTrash,
                                        contentDescription = if (person.isActive) "Archive" else "Restore",
                                        tint = if (person.isActive) ext.rose else ext.emerald
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        var name by remember(editingStaff) { mutableStateOf(editingStaff?.name.orEmpty()) }
        var code by remember(editingStaff) { mutableStateOf(editingStaff?.staffCode.orEmpty()) }
        var dept by remember(editingStaff) { mutableStateOf(editingStaff?.department ?: "Academic") }
        var role by remember(editingStaff) { mutableStateOf(editingStaff?.role ?: "Teacher") }
        var loc by remember(editingStaff) { mutableStateOf(editingStaff?.assignedLocation.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            containerColor = if (ext.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(if (editingStaff != null) "Edit Staff Record" else "Register Staff Member", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true, colors = glassFieldColors(), shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Staff Code") }, singleLine = true, colors = glassFieldColors(), shape = RoundedCornerShape(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Academic", "Operation").forEach { d ->
                            FilterChip(selected = dept.equals(d, true), onClick = { dept = d }, label = { Text(d) })
                        }
                    }
                    OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Role / Group") }, singleLine = true, colors = glassFieldColors(), shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(value = loc, onValueChange = { loc = it }, label = { Text("Assigned Location / Class Wing") }, singleLine = true, colors = glassFieldColors(), shape = RoundedCornerShape(14.dp))
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && code.isNotBlank(),
                    onClick = {
                        val isNew = editingStaff == null
                        val model = StaffMember(
                            id = editingStaff?.id ?: UUID.randomUUID().toString(),
                            staffNumber = code.trim(),
                            staffCode = code.trim(),
                            name = name.trim(),
                            category = if (dept == "Operation") "Non-Academic" else "Academic",
                            staffGroup = role.ifBlank { dept },
                            role = role.ifBlank { "Teacher" },
                            department = dept,
                            assignedLocation = loc.trim(),
                            isActive = editingStaff?.isActive ?: true
                        )
                        val conflict = staffList.firstOrNull {
                            it.staffCode.equals(model.staffCode, true) && it.id != model.id
                        }
                        if (conflict != null) {
                            conflictHolder = conflict
                            pendingSavePair = model to isNew
                            showAddEditDialog = false
                        } else {
                            onSaveStaff(model, isNew) { showAddEditDialog = false }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (conflictHolder != null && pendingSavePair != null) {
        val holder = conflictHolder!!
        val (newStaff, isNew) = pendingSavePair!!
        var reassignedCode by remember(holder.id) { mutableStateOf("${holder.staffCode}-OLD") }
        AlertDialog(
            onDismissRequest = { conflictHolder = null; pendingSavePair = null },
            containerColor = if (ext.isDark) Color(0xFF151923) else Color(0xFFF6F8FC),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Duplicate Staff Code Conflict", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Code ${holder.staffCode} is currently held by ${holder.name}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = reassignedCode,
                        onValueChange = { reassignedCode = it },
                        label = { Text("Reassign Existing Holder Code To:") },
                        singleLine = true,
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onReassignConflict(holder, reassignedCode, newStaff, isNew) {
                        conflictHolder = null; pendingSavePair = null
                    }
                }) { Text("Reassign & Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    onArchiveConflict(holder, newStaff, isNew) {
                        conflictHolder = null; pendingSavePair = null
                    }
                }) { Text("Archive Old Holder") }
            }
        )
    }

    selectedDossierStaff?.let { staff ->
        val staffReports = reports.filter {
            (staff.id.isNotBlank() && it.staffId == staff.id) ||
                (staff.staffCode.isNotBlank() && it.staffCode.equals(staff.staffCode, true)) ||
                it.staffName.equals(staff.name, true)
        }
        val sev = SeverityLevel.fromCount(staffReports.size)
        ModalBottomSheet(
            onDismissRequest = { selectedDossierStaff = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = if (ext.isDark) Color(0xFF131720) else Color(0xFFF4F7FB),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        MonoCodeText(staff.staffCode)
                        Text(
                            text = staff.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${staff.department} • ${staff.role}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SeverityBadge(sev)
                }
                MicroLabel("${staffReports.size} Recorded Exceptions")
                staffReports.take(8).forEach { r ->
                    GlassCard(onClick = {
                        selectedDossierStaff = null
                        onSelectReport(r)
                    }, cornerRadius = 20.dp, contentPadding = 14.dp, modifier = Modifier.fillMaxWidth()) {
                        MicroLabel("${r.incidentDate} • ${r.term}")
                        Text(
                            text = r.complianceCheck,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = r.exceptionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}
