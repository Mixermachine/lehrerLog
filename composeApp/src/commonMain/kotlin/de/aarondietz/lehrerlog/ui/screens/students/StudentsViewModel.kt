package de.aarondietz.lehrerlog.ui.screens.students

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import de.aarondietz.lehrerlog.data.SchoolClass

class StudentsViewModel: ViewModel() {

    val classes = mutableStateListOf<SchoolClass>()


}