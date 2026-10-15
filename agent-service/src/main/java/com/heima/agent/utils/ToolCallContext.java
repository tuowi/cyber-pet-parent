package com.heima.agent.utils;

import com.heima.agent.domain.vo.ToolCallRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ToolCallContext {
    private static final ThreadLocal<List<ToolCallRecord>> RECORDS = new ThreadLocal<>();

    public static void record(String tool, String summary) {
        List<ToolCallRecord> list = RECORDS.get();
        if (list == null) {
            list = new ArrayList<>();
            RECORDS.set(list);
        }
        list.add(new ToolCallRecord(tool, summary));
    }

    public static List<ToolCallRecord> getAndClear() {
        List<ToolCallRecord> list = RECORDS.get();
        RECORDS.remove();
        return list != null ? list : Collections.emptyList();
    }
}
