package com.bombless.luaandroid.ipc;

import android.os.ParcelFileDescriptor;
import com.bombless.luaandroid.ipc.JobInfo;
import com.bombless.luaandroid.ipc.JobRequest;
import com.bombless.luaandroid.ipc.IJobCallback;

interface ILuaRuntimeService {
    int getApiVersion();
    String getRuntimeInfo();

    // Returns a stable job ID. Must not block for script execution.
    String submit(in JobRequest request);

    JobInfo getJob(String jobId);
    List<JobInfo> listJobs(int limit, long beforeCreatedAtMillis);
    boolean cancel(String jobId);

    // Register callback for one job. Callback may be invoked on a Binder thread.
    void registerCallback(String jobId, IJobCallback callback);
    void unregisterCallback(String jobId, IJobCallback callback);

    // Open a generated artifact by its opaque artifact ID.
    ParcelFileDescriptor openResult(String jobId, String artifactId);
}
