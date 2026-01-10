package de.aarondietz.lehrerlog.ui.screens.tasks

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import de.aarondietz.lehrerlog.data.Task

class TasksViewModel: ViewModel() {
    val tasks = mutableStateListOf<Task>()
}