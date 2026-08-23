package com.whereareyou.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.whereareyou.app.contacts.ContactEditScreen
import com.whereareyou.app.permissions.PermissionsScreen
import com.whereareyou.app.platform.AppContainer
import com.whereareyou.core.model.TrustedContactId

private object Routes {
    const val HOME = "home"
    const val ADD_CONTACT = "contact/new"
    const val EDIT_CONTACT = "contact/{contactId}/edit"
    const val PERMISSIONS = "permissions"

    fun editContact(id: String) = "contact/$id/edit"
}

@Composable
fun WhereAreYouApp(container: AppContainer) {
    val navController = rememberNavController()
    val repository = container.contactRepository

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onAddContact = { navController.navigate(Routes.ADD_CONTACT) },
                onEditContact = { id -> navController.navigate(Routes.editContact(id.value)) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }
        composable(Routes.ADD_CONTACT) {
            ContactEditScreen(
                repository = repository,
                contactIdToEdit = null,
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EDIT_CONTACT,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId")
            ContactEditScreen(
                repository = repository,
                contactIdToEdit = contactId?.let { TrustedContactId(it) },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
    }
}
