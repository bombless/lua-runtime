package com.bombless.luaandroid

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.bombless.luaandroid.ipc.ILuaRuntimeService
import com.bombless.luaandroid.ipc.JobRequest

class MainActivity : Activity() {
    private var runtime: ILuaRuntimeService? = null
    private var bound = false
    private lateinit var sourceInput: EditText
    private lateinit var statusView: TextView
    private lateinit var runButton: Button
    private val handler = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            runtime = ILuaRuntimeService.Stub.asInterface(service)
            statusView.text = "已连接 Lua Runtime · API ${runtime?.getApiVersion()}\n${runtime?.getRuntimeInfo()}"
            runButton.isEnabled = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            runtime = null
            runButton.isEnabled = false
            statusView.text = "运行时服务已断开"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val title = TextView(this).apply { text = "Lua Android 控制台"; textSize = 24f }
        statusView = TextView(this).apply {
            text = "正在连接运行时服务…"
            textSize = 14f
            setPadding(0, 12, 0, 16)
        }
        sourceInput = EditText(this).apply {
            setText("print('Hello from Lua Android!')\nreturn 1 + 2")
            hint = "输入 Lua 脚本"
            minLines = 8
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        runButton = Button(this).apply {
            text = "运行 Lua 脚本"
            isEnabled = false
            setOnClickListener { submitScript() }
        }
        content.addView(title)
        content.addView(statusView)
        content.addView(sourceInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(runButton)
        setContentView(content)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4102)
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent("com.bombless.luaandroid.action.BIND_RUNTIME").setPackage(packageName)
        bound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound) statusView.text = "无法连接 Lua Runtime 服务"
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        if (bound) unbindService(connection)
        bound = false
        runtime = null
        super.onStop()
    }

    private fun submitScript() {
        val service = runtime ?: run { statusView.text = "运行时尚未连接"; return }
        val source = sourceInput.text.toString()
        if (source.isBlank()) { statusView.text = "请先输入 Lua 脚本"; return }
        runButton.isEnabled = false
        try {
            val request = JobRequest().apply {
                apiVersion = 1
                this.source = source
                timeoutMillis = 30_000L
            }
            val jobId = service.submit(request)
            statusView.text = "任务已提交：$jobId\n正在执行…"
            pollJob(jobId, 0)
        } catch (error: Exception) {
            statusView.text = "提交失败：${error.message}"
            runButton.isEnabled = true
        }
    }

    private fun pollJob(jobId: String, attempt: Int) {
        handler.postDelayed({
            try {
                val job = runtime?.getJob(jobId)
                if (job == null) {
                    statusView.text = "任务不存在：$jobId"
                    runButton.isEnabled = true
                } else if (job.state == "QUEUED" || job.state == "RUNNING") {
                    statusView.text = "任务：${job.state}\nID：$jobId"
                    if (attempt < 120) pollJob(jobId, attempt + 1) else {
                        statusView.text = "等待超时，请稍后重新查看任务 $jobId"
                        runButton.isEnabled = true
                    }
                } else {
                    statusView.text = buildString {
                        append("任务：${job.state}\nID：$jobId\n退出码：${job.exitCode ?: "—"}")
                        job.returnValueJson?.let { append("\n返回值：$it") }
                        job.errorMessage?.let { append("\n错误：$it") }
                        job.errorCode?.let { append("\n错误代码：$it") }
                    }
                    runButton.isEnabled = true
                }
            } catch (error: Exception) {
                statusView.text = "读取任务状态失败：${error.message}"
                runButton.isEnabled = true
            }
        }, 500L)
    }
}