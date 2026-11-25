package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.ui.graphics.vector.ImageVector

private data class NavItem(
  val title: String,
  val route: String,
  val icon: ImageVector,
  val subtitle: String
)

class ComplianceNavigationUnitTest {

  @Test
  fun `verify navigation item creation`() {
    val nav = NavItem("Dashboard", "/", Icons.Default.Dashboard, "Executive Overview")
    assertEquals("Dashboard", nav.title)
    assertEquals("/", nav.route)
    assertNotNull(nav.icon)
  }

  @Test
  fun `verify basic data structure`() {
    val nav = NavItem("My Exceptions", "/my-exceptions", Icons.Default.Dashboard, "My Desk")
    assertEquals("My Exceptions", nav.title)
    assertEquals("/my-exceptions", nav.route)
  }
}
