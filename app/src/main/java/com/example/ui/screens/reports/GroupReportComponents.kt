package com.example.ui.screens.reports

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.data.local.ComplianceTaxonomy
import com.example.data.model.Classroom
import com.example.data.model.GroupReportMember
import com.example.data.model.StaffMember
import com.example.data.model.SubjectItem
import com.example.domain.GroupReportHelpers
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.MicroLabel
import com.example.ui.theme.ComplianceTheme
import com.example.ui.theme.LocalCompliancePalette

@Composable
fun AcademicReportModeSwitcher(
    isGroupReport: Boolean,
    onSelectMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.trackFill)
            .border(1.dp, p.borderBottom, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModePill(
            label = "Single Staff",
            icon = Icons.Default.Person,
            selected = !isGroupReport,
            onClick = { onSelectMode(false) },
            modifier = Modifier
                .weight(1f)
                .testTag("mode_single_staff")
        )
        ModePill(
            label = "Group Report (Combined)",
            icon = Icons.Default.Groups,
            selected = isGroupReport,
            onClick = { onSelectMode(true) },
            modifier = Modifier
                .weight(1.15f)
                .testTag("mode_group_report")
        )
    }
}

@Composable
private fun ModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalCompliancePalette.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) p.tangerine.copy(alpha = 0.20f) else Color.Transparent,
        label = "mode_pill_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) p.tangerine else Color.Transparent,
        label = "mode_pill_border"
    )
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) p.tangerine else p.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) p.tangerine else p.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AcademicGroupStaffSelector(
    filteredStaff: List<StaffMember>,
    groupMembers: List<GroupReportMember>,
    departmentFilter: String,
    onDepartmentFilterChange: (String) -> Unit,
    onToggleStaff: (StaffMember) -> Unit,
    onRemoveMember: (GroupReportMember) -> Unit,
    onOpenClassAssignment: () -> Unit,
    onOpenSubjectAssignment: () -> Unit
) {
    val p = LocalCompliancePalette.current
    val ext = ComplianceTheme.extended

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Real-time Selection Counter Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MicroLabel("Department Filter & Multi-Select")
            Surface(
                shape = CircleShape,
                color = if (groupMembers.size >= 2) p.emerald.copy(alpha = 0.16f) else p.tangerine.copy(alpha = 0.16f),
                border = BorderStroke(
                    1.dp,
                    if (groupMembers.size >= 2) p.emerald.copy(alpha = 0.45f) else p.tangerine.copy(alpha = 0.45f)
                ),
                modifier = Modifier.testTag("selected_staff_counter_badge")
            ) {
                Text(
                    text = "Selected Staff (${groupMembers.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (groupMembers.size >= 2) p.emerald else p.tangerine,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Department Filter Pills (Keep checked members across department switches)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ComplianceTaxonomy.academicDepartments.forEach { dept ->
                FilterChip(
                    selected = departmentFilter.equals(dept, ignoreCase = true),
                    onClick = { onDepartmentFilterChange(dept) },
                    label = { Text(dept, maxLines = 1) },
                    modifier = Modifier.testTag("dept_filter_${dept.lowercase().replace(' ', '_')}")
                )
            }
        }

        // Selected Teacher Chips with Quick-Remove (×)
        if (groupMembers.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupMembers.forEach { member ->
                    val initials = remember(member.staffName) {
                        member.staffName
                            .split(" ")
                            .filter { it.isNotBlank() }
                            .take(2)
                            .joinToString("") { it.first().uppercase() }
                            .ifBlank { "ST" }
                    }
                    Surface(
                        shape = CircleShape,
                        color = p.tangerine.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, p.tangerine.copy(alpha = 0.42f))
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 5.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(p.tangerine),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Column {
                                Text(
                                    text = member.staffName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = p.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = member.staffGroup.ifBlank { "Academic" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = p.textSecondary,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(p.trackFill)
                                    .clickable { onRemoveMember(member) }
                                    .testTag("remove_group_member_${member.staffCode}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${member.staffName}",
                                    tint = p.textPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Multi-Select Staff List with Checkboxes
        val selectedIds = remember(groupMembers) {
            groupMembers.map { it.staffId.lowercase() }.toSet()
        }
        val selectedCodes = remember(groupMembers) {
            groupMembers.mapNotNull { it.staffCode.takeIf(String::isNotBlank)?.lowercase() }.toSet()
        }

        Surface(
            color = ext.glassBottom,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, ext.glassRimEnd),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (filteredStaff.isEmpty()) {
                Text(
                    text = "No academic staff match the current search or department filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(215.dp)
                        .testTag("group_staff_checkbox_list")
                ) {
                    items(filteredStaff, key = { it.id.ifBlank { "${it.staffCode}_${it.name}" } }) { staff ->
                        val isChecked = staff.id.lowercase() in selectedIds ||
                            (staff.staffCode.isNotBlank() && staff.staffCode.lowercase() in selectedCodes)
                        val resolvedDept = remember(staff.id, staff.staffGroup, staff.subjects) {
                            GroupReportHelpers.resolveStaffDepartment(staff)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleStaff(staff) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleStaff(staff) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = p.tangerine,
                                    checkmarkColor = Color.White
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = staff.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                    color = p.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOfNotNull(
                                        staff.staffCode.takeIf { it.isNotBlank() },
                                        resolvedDept.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = p.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = p.borderBottom.copy(alpha = 0.45f))
                    }
                }
            }
        }

        // Dynamic Class & Subject Action Buttons
        val classBtnLabel = remember(groupMembers) {
            when {
                groupMembers.isEmpty() -> "Select Classes (0 staff)"
                groupMembers.all { it.className.isBlank() } -> "Select Classes (${groupMembers.size} staff)"
                else -> {
                    val assigned = groupMembers.map { it.className.trim() }.filter { it.isNotBlank() }
                    if (assigned.size == groupMembers.size && assigned.distinct().size == 1) {
                        "All: ${assigned.first()}"
                    } else {
                        "${assigned.size} Assigned (Individual)"
                    }
                }
            }
        }

        val subjectBtnLabel = remember(groupMembers) {
            when {
                groupMembers.isEmpty() -> "Select Subjects (0 staff)"
                groupMembers.all { it.subjectName.isBlank() } -> "Select Subjects (${groupMembers.size} staff)"
                else -> {
                    val assigned = groupMembers.map { it.subjectName.trim() }.filter { it.isNotBlank() }
                    if (assigned.size == groupMembers.size && assigned.distinct().size == 1) {
                        "All: ${assigned.first()}"
                    } else {
                        "${assigned.size} Assigned (Individual)"
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenClassAssignment,
                enabled = groupMembers.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("group_assign_class_button")
            ) {
                Icon(Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(classBtnLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onOpenSubjectAssignment,
                enabled = groupMembers.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("group_assign_subject_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(subjectBtnLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ApplyGroupModalWindowBlur(
    dimAmount: Float = 0.40f,
    blurRadiusPx: Int = 40
) {
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.setDimAmount(dimAmount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = blurRadiusPx
            }
        }
    }
}

@Composable
fun GroupClassAssignmentDialog(
    members: List<GroupReportMember>,
    classOptions: List<Classroom>,
    onApplyAll: (Classroom) -> Unit,
    onUpdateMemberClass: (staffId: String, Classroom) -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalCompliancePalette.current
    var showGlobalMenu by remember { mutableStateOf(false) }
    var globalSelection by remember { mutableStateOf<Classroom?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ApplyGroupModalWindowBlur(dimAmount = 0.40f, blurRadiusPx = 40)
        val modalShape = RoundedCornerShape(26.dp)
        val modalGlassTop = if (p.isDark) Color(0xFF151A25).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.94f)
        val modalGlassBottom = if (p.isDark) Color(0xFF0E121A).copy(alpha = 0.93f) else Color(0xFFF2F6FC).copy(alpha = 0.92f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .shadow(24.dp, modalShape, ambientColor = p.shadow, spotColor = p.shadow)
                    .clip(modalShape)
                    .background(Brush.verticalGradient(listOf(modalGlassTop, modalGlassBottom)), modalShape)
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(p.borderTop, p.borderBottom)),
                        modalShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        MicroLabel("GROUP ASSIGNMENT")
                        Text(
                            text = "Assign Classes (${members.size} Staff)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = p.textPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = p.textPrimary)
                    }
                }

                // Global "Apply to All" Selector
                Surface(
                    color = p.tangerine.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, p.tangerine.copy(alpha = 0.38f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, tint = p.tangerine, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Apply 1 Class to All Selected Staff",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = p.tangerine
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showGlobalMenu = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("group_class_apply_all_dropdown")
                            ) {
                                Text(
                                    text = globalSelection?.let { "Applied All: ${it.name}" } ?: "Select Class to Apply to All",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = showGlobalMenu, onDismissRequest = { showGlobalMenu = false }) {
                                classOptions.forEach { cls ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (cls.id == "NONE") "None (Outside Classroom)" else cls.name,
                                                fontWeight = if (cls.id == "NONE") FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            globalSelection = cls
                                            onApplyAll(cls)
                                            showGlobalMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                MicroLabel("Individual Teacher Overrides")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    members.forEach { member ->
                        var showRowMenu by remember(member.staffId) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.trackFill)
                                .border(1.dp, p.borderBottom, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.staffName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = p.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = member.staffGroup.ifBlank { member.staffCode },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = p.textSecondary,
                                    maxLines = 1
                                )
                            }
                            Box {
                                OutlinedButton(
                                    onClick = { showRowMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.width(136.dp)
                                ) {
                                    Text(
                                        text = member.className.ifBlank { "Select" },
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = showRowMenu, onDismissRequest = { showRowMenu = false }) {
                                    classOptions.forEach { cls ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = if (cls.id == "NONE") "None (Outside Classroom)" else cls.name,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (member.className.equals(cls.name, true)) {
                                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onUpdateMemberClass(member.staffId, cls)
                                                showRowMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                GlowingPrimaryButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("group_class_done_button")
                ) {
                    Text("Done", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun GroupSubjectAssignmentDialog(
    members: List<GroupReportMember>,
    subjectOptions: List<SubjectItem>,
    onApplyAll: (SubjectItem) -> Unit,
    onUpdateMemberSubject: (staffId: String, SubjectItem) -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalCompliancePalette.current
    var showGlobalMenu by remember { mutableStateOf(false) }
    var globalSelection by remember { mutableStateOf<SubjectItem?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ApplyGroupModalWindowBlur(dimAmount = 0.40f, blurRadiusPx = 40)
        val modalShape = RoundedCornerShape(26.dp)
        val modalGlassTop = if (p.isDark) Color(0xFF151A25).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.94f)
        val modalGlassBottom = if (p.isDark) Color(0xFF0E121A).copy(alpha = 0.93f) else Color(0xFFF2F6FC).copy(alpha = 0.92f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .shadow(24.dp, modalShape, ambientColor = p.shadow, spotColor = p.shadow)
                    .clip(modalShape)
                    .background(Brush.verticalGradient(listOf(modalGlassTop, modalGlassBottom)), modalShape)
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(p.borderTop, p.borderBottom)),
                        modalShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        MicroLabel("GROUP ASSIGNMENT")
                        Text(
                            text = "Assign Subjects (${members.size} Staff)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = p.textPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = p.textPrimary)
                    }
                }

                Surface(
                    color = p.tangerine.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, p.tangerine.copy(alpha = 0.38f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, tint = p.tangerine, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Apply 1 Subject to All Selected Staff",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = p.tangerine
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showGlobalMenu = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("group_subject_apply_all_dropdown")
                            ) {
                                Text(
                                    text = globalSelection?.let { "Applied All: ${it.name}" } ?: "Select Subject to Apply to All",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = showGlobalMenu, onDismissRequest = { showGlobalMenu = false }) {
                                subjectOptions.forEach { subj ->
                                    DropdownMenuItem(
                                        text = { Text(subj.name) },
                                        onClick = {
                                            globalSelection = subj
                                            onApplyAll(subj)
                                            showGlobalMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                MicroLabel("Individual Teacher Overrides")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    members.forEach { member ->
                        var showRowMenu by remember(member.staffId) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.trackFill)
                                .border(1.dp, p.borderBottom, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.staffName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = p.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = member.staffGroup.ifBlank { member.staffCode },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = p.textSecondary,
                                    maxLines = 1
                                )
                            }
                            Box {
                                OutlinedButton(
                                    onClick = { showRowMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.width(136.dp)
                                ) {
                                    Text(
                                        text = member.subjectName.ifBlank { "Select" },
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = showRowMenu, onDismissRequest = { showRowMenu = false }) {
                                    subjectOptions.forEach { subj ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(subj.name, modifier = Modifier.weight(1f))
                                                    if (member.subjectName.equals(subj.name, true)) {
                                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onUpdateMemberSubject(member.staffId, subj)
                                                showRowMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                GlowingPrimaryButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("group_subject_done_button")
                ) {
                    Text("Done", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
