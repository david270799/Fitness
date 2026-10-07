package com.iron.fitness.feature.cardio.data

import android.content.res.Resources
import androidx.annotation.StringRes
import com.iron.fitness.R
import com.iron.fitness.core.domain.BlockType
import com.iron.fitness.core.domain.IntervalBlock

/** Встроенный шаблон интервальной программы. Подписи берутся из ресурсов. */
data class IntervalTemplate(
    val key: String,
    @StringRes val name: Int,
    @StringRes val description: Int,
    val workMet: Double,
    val build: (Resources) -> List<IntervalBlock>,
) {
    fun toProgram(res: Resources): IntervalProgram =
        IntervalProgram(name = res.getString(name), blocks = build(res), workMet = workMet)
}

object IntervalTemplates {
    private fun step(type: BlockType, sec: Int, label: String? = null) = IntervalBlock(type, sec, label)

    val all: List<IntervalTemplate> = listOf(
        IntervalTemplate("tabata", R.string.tpl_tabata, R.string.tpl_tabata_desc, IntervalProgram.WORK_MET_HARD) { r ->
            listOf(
                step(BlockType.WARMUP, 120, r.getString(R.string.tpl_label_warmup)),
                IntervalBlock(
                    BlockType.REPEAT,
                    rounds = 8,
                    children = listOf(step(BlockType.WORK, 20), step(BlockType.REST, 10)),
                ),
                step(BlockType.COOLDOWN, 60, r.getString(R.string.tpl_label_cooldown)),
            )
        },
        IntervalTemplate("hiit_30_30", R.string.tpl_hiit, R.string.tpl_hiit_desc, IntervalProgram.WORK_MET_HARD) { r ->
            listOf(
                step(BlockType.WARMUP, 180, r.getString(R.string.tpl_label_warmup)),
                IntervalBlock(
                    BlockType.REPEAT,
                    rounds = 10,
                    children = listOf(step(BlockType.WORK, 30), step(BlockType.REST, 30)),
                ),
                step(BlockType.COOLDOWN, 120, r.getString(R.string.tpl_label_cooldown)),
            )
        },
        IntervalTemplate("run_intervals", R.string.tpl_run, R.string.tpl_run_desc, IntervalProgram.WORK_MET_MAX) { r ->
            listOf(
                step(BlockType.WARMUP, 300, r.getString(R.string.tpl_label_easy_run)),
                IntervalBlock(
                    BlockType.REPEAT,
                    rounds = 6,
                    children = listOf(
                        step(BlockType.WORK, 60, r.getString(R.string.tpl_label_fast)),
                        step(BlockType.REST, 120, r.getString(R.string.tpl_label_jog)),
                    ),
                    skipLastRest = false,
                ),
                step(BlockType.COOLDOWN, 300, r.getString(R.string.tpl_label_walk)),
            )
        },
        IntervalTemplate("emom", R.string.tpl_emom, R.string.tpl_emom_desc, IntervalProgram.WORK_MET_HARD) { r ->
            listOf(
                IntervalBlock(
                    BlockType.REPEAT,
                    rounds = 10,
                    children = listOf(step(BlockType.WORK, 60, r.getString(R.string.tpl_label_emom))),
                ),
            )
        },
        IntervalTemplate("circuit", R.string.tpl_circuit, R.string.tpl_circuit_desc, IntervalProgram.WORK_MET_HARD) { r ->
            listOf(
                step(BlockType.WARMUP, 180, r.getString(R.string.tpl_label_warmup)),
                IntervalBlock(
                    BlockType.REPEAT,
                    rounds = 3,
                    children = listOf(
                        step(BlockType.WORK, 40, r.getString(R.string.tpl_label_squats)),
                        step(BlockType.REST, 20),
                        step(BlockType.WORK, 40, r.getString(R.string.tpl_label_pushups)),
                        step(BlockType.REST, 20),
                        step(BlockType.WORK, 40, r.getString(R.string.tpl_label_lunges)),
                        step(BlockType.REST, 20),
                        step(BlockType.WORK, 40, r.getString(R.string.tpl_label_plank)),
                        step(BlockType.REST, 60),
                    ),
                ),
                step(BlockType.COOLDOWN, 120, r.getString(R.string.tpl_label_cooldown)),
            )
        },
    )
}
