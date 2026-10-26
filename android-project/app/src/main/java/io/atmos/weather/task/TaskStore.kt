package io.atmos.weather.task

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TaskStore {
    private const val FILE="thinn_personal_tasks"
    private const val KEY="tasks_v1"
    fun load(context:Context):List<ThinnTask> = runCatching {
        val raw=context.getSharedPreferences(FILE,Context.MODE_PRIVATE).getString(KEY,"[]")?:"[]"
        val array=JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o=array.getJSONObject(i)
                val task=ThinnTask(
                    id=o.getString("id"),title=o.getString("title"),note=o.optString("note"),
                    year=o.getInt("year"),month=o.getInt("month"),day=o.getInt("day"),
                    hour=o.getInt("hour"),minute=o.getInt("minute"),
                    completed=o.optBoolean("completed"),notify=o.optBoolean("notify",true)
                )
                // Discard invalid/tampered dates instead of crashing UI or scheduler.
                task
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun save(context:Context,tasks:List<ThinnTask>) {
        val arr=JSONArray()
        tasks.forEach {t-> arr.put(JSONObject().apply {
            put("id",t.id);put("title",t.title);put("note",t.note)
            put("year",t.year);put("month",t.month);put("day",t.day)
            put("hour",t.hour);put("minute",t.minute)
            put("completed",t.completed);put("notify",t.notify)
        }) }
        check(context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit()
            .putString(KEY,arr.toString()).commit()) { "事項儲存失敗" }
        TaskReminderScheduler.sync(context,tasks)
        io.atmos.weather.widgets.WidgetSync.refreshCached(context)
    }
}
