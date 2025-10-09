package de.aarondietz.lehrerlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.data.SchoolClass
import de.aarondietz.lehrerlog.data.Student
import de.aarondietz.lehrerlog.ui.composables.AddClassDialog
import de.aarondietz.lehrerlog.ui.composables.AddStudentDialog
import de.aarondietz.lehrerlog.ui.composables.ExpandableClassCard

@Composable
fun StudentsScreen() {
    val classes = remember { mutableStateListOf<SchoolClass>() }
    val expandedClasses = remember { mutableStateMapOf<String, Boolean>() }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var selectedClassForStudent by remember { mutableStateOf<SchoolClass?>(null) }

    LaunchedEffect(true) {
        repeat(10) {
            val myClass = SchoolClass(name = "Test")
            repeat(50) {
                myClass.students.add(Student(
                    firstName = "SLe",
                    lastName = "sda",
                ))
            }

            classes.add(myClass)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddClassDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Klasse hinzufügen")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (classes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Keine Klassen vorhanden",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = classes,
                    key = { it.id }
                ) { schoolClass ->
                    ExpandableClassCard(
                        schoolClass = schoolClass,
                        isExpanded = expandedClasses[schoolClass.id] ?: false,
                        onExpandClick = {
                            expandedClasses[schoolClass.id] = !(expandedClasses[schoolClass.id] ?: false)
                        },
                        onAddStudent = {
                            selectedClassForStudent = schoolClass
                        },
                        onDeleteClass = {
                            classes.remove(schoolClass)
                            expandedClasses.remove(schoolClass.id)
                        },
                        onDeleteStudent = { student ->
                            schoolClass.students.remove(student)
                        }
                    )
                }
            }
        }
    }

    if (showAddClassDialog) {
        AddClassDialog(
            onDismiss = { showAddClassDialog = false },
            onConfirm = { className ->
                classes.add(SchoolClass(name = className))
                showAddClassDialog = false
            }
        )
    }

    selectedClassForStudent?.let { schoolClass ->
        AddStudentDialog(
            onDismiss = { selectedClassForStudent = null },
            onConfirm = { student ->
                schoolClass.students.add(student)
                selectedClassForStudent = null
            }
        )
    }
}
