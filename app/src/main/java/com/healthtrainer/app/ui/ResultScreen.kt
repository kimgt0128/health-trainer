package com.healthtrainer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthtrainer.app.replay.SkeletonReplayFrame
import com.healthtrainer.core.tracker.ExerciseSession
import com.healthtrainer.core.tracker.RepRecord

/**
 * Post-session summary: total reps / valid reps, the invalid-rep list grouped by set (rendered via
 * [FeedbackText.invalidRepLine] as `"N세트 M회차: <사유>"`), and an entry into the 3D replay.
 *
 * Totals are derived straight from the `:core` [ExerciseSession]: `sets.flatMap { it.reps }`. Rep
 * validity is `:core`'s ([RepRecord.valid]); `:app` only presents it.
 *
 * NOTE (requires device): rendering is unverified on an SDK-less machine; the data derivation is
 * inspectable.
 *
 * @param onReplay  open the replay viewer (the host provides the captured [SkeletonReplayFrame]s).
 * @param onRestart return to the exercise screen for a new session.
 */
@Composable
fun ResultScreen(
    session: ExerciseSession,
    replayFrames: List<SkeletonReplayFrame>,
    onReplay: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allReps = session.sets.flatMap { it.reps }
    val totalReps = allReps.size
    val validReps = allReps.count { it.valid }
    val invalidBySet: List<Pair<Int, List<RepRecord>>> = session.sets
        .map { it.setNo to it.reps.filter { rep -> !rep.valid } }
        .filter { it.second.isNotEmpty() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "운동 결과",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text("총 ${totalReps}회 / 성공 ${validReps}회", fontSize = 18.sp)
        Text("세트 수: ${session.sets.size}", fontSize = 16.sp)

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("교정이 필요한 회차", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (invalidBySet.isEmpty()) {
                item { Text("모든 회차가 올바른 자세였습니다 🎉") }
            } else {
                invalidBySet.forEach { (setNo, reps) ->
                    item {
                        Text(
                            text = "${setNo}세트",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(reps) { rep ->
                        // FeedbackText.invalidRepLine(rep) -> e.g. "1세트 2회차: 스쿼트 깊이 부족"
                        Text(FeedbackText.invalidRepLine(rep))
                    }
                }
            }
        }

        Button(
            onClick = onReplay,
            enabled = replayFrames.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("3D 리플레이 보기") }

        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("새 운동 시작") }
    }
}
