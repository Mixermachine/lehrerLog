package de.aarondietz.lehrerlog.ui.test

object UiTestTags {
    const val loginEmailField = "login_email_field"
    const val loginPasswordField = "login_password_field"
    const val loginSubmitButton = "login_submit_button"
    const val loginRegisterButton = "login_register_button"
    const val loginParentInviteButton = "login_parent_invite_button"

    const val registerSchoolQueryField = "register_school_query_field"
    const val registerFirstNameField = "register_first_name_field"
    const val registerLastNameField = "register_last_name_field"
    const val registerEmailField = "register_email_field"
    const val registerPasswordField = "register_password_field"
    const val registerConfirmPasswordField = "register_confirm_password_field"
    const val registerSubmitButton = "register_submit_button"
    const val registerLoginButton = "register_login_button"

    fun registerSchoolSuggestion(schoolCode: String): String {
        return "register_school_suggestion_$schoolCode"
    }

    const val parentInviteCodeField = "parent_invite_code_field"
    const val parentInviteFirstNameField = "parent_invite_first_name_field"
    const val parentInviteLastNameField = "parent_invite_last_name_field"
    const val parentInviteEmailField = "parent_invite_email_field"
    const val parentInvitePasswordField = "parent_invite_password_field"
    const val parentInviteSubmitButton = "parent_invite_submit_button"
    const val parentInviteBackButton = "parent_invite_back_button"

    const val settingsRefreshQuotaButton = "settings_refresh_quota_button"
    const val settingsRefreshLogsButton = "settings_refresh_logs_button"
    const val settingsShareLogsButton = "settings_share_logs_button"
    const val settingsClearLogsButton = "settings_clear_logs_button"
    const val settingsLogoutButton = "settings_logout_button"
    const val studentsAddClassFab = "students_add_class_fab"
    const val addClassDialogNameField = "add_class_dialog_name_field"
    const val addClassDialogConfirmButton = "add_class_dialog_confirm_button"
    const val addStudentDialogFirstNameField = "add_student_dialog_first_name_field"
    const val addStudentDialogLastNameField = "add_student_dialog_last_name_field"
    const val addStudentDialogConfirmButton = "add_student_dialog_confirm_button"
    const val studentsDeleteStudentConfirmButton = "students_delete_student_confirm_button"

    fun homeResolvePunishmentButton(studentId: String): String {
        return "home_resolve_punishment_$studentId"
    }

    fun studentsClassExpandButton(classId: String): String {
        return "students_class_expand_$classId"
    }

    fun studentsAddStudentButton(classId: String): String {
        return "students_add_student_$classId"
    }

    fun studentsDeleteClassButton(classId: String): String {
        return "students_delete_class_$classId"
    }

    fun studentsInviteParentButton(studentId: String): String {
        return "students_invite_parent_$studentId"
    }

    fun studentsParentLinksButton(studentId: String): String {
        return "students_parent_links_$studentId"
    }

    fun studentsDeleteStudentButton(studentId: String): String {
        return "students_delete_student_$studentId"
    }

    fun parentStudentItem(studentId: String): String {
        return "parent_student_item_$studentId"
    }

    const val tasksAddFab = "tasks_add_fab"
    const val tasksClassSelectorToggle = "tasks_class_selector_toggle"
    const val addTaskDialogTitleField = "add_task_dialog_title_field"
    const val addTaskDialogDescriptionField = "add_task_dialog_description_field"
    const val addTaskDialogDateField = "add_task_dialog_date_field"
    const val addTaskDialogOpenDatePickerButton = "add_task_dialog_open_date_picker_button"
    const val addTaskDialogUploadButton = "add_task_dialog_upload_button"
    const val addTaskDialogConfirmButton = "add_task_dialog_confirm_button"
    const val addTaskDialogCancelButton = "add_task_dialog_cancel_button"
    const val taskDetailAssignmentUploadButton = "task_detail_assignment_upload_button"

    fun tasksClassMenuItem(classId: String): String {
        return "tasks_class_menu_item_$classId"
    }

    fun tasksCard(taskId: String): String {
        return "tasks_card_$taskId"
    }

    fun taskDetailMarkInPersonButton(studentId: String): String {
        return "task_detail_mark_in_person_$studentId"
    }
}
