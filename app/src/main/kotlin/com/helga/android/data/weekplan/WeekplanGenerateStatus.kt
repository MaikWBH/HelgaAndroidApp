package com.helga.android.data.weekplan

import com.helga.android.data.remote.dto.WeekplanAssignmentDto

sealed interface WeekplanGenerateStatus {
    data object Idle : WeekplanGenerateStatus
    data object Loading : WeekplanGenerateStatus
    data class Proposal(
        val assignments: List<WeekplanAssignmentDto>,
        val warnings: List<String> = emptyList(),
    ) : WeekplanGenerateStatus
    data class Error(val message: String) : WeekplanGenerateStatus
}
