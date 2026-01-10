package de.aarondietz.lehrerlog.ui.screens.tasks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.aarondietz.lehrerlog.data.SchoolClass
import de.aarondietz.lehrerlog.data.Student
import de.aarondietz.lehrerlog.data.Task
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.add_class
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TasksScreen(
    viewModel: TasksViewModel = koinViewModel()
) {
    var showAddTaskDialog by remember { mutableStateOf(false) }

    LaunchedEffect(true) {
        repeat(10) {
            val myClass = Task(name = "Test")

            viewModel.tasks.add(myClass)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_class))
            }
        }
    ) {

    }
}

@Preview
@Composable
fun TasksScreenPreview() {
    TasksScreen(TasksViewModel())
}