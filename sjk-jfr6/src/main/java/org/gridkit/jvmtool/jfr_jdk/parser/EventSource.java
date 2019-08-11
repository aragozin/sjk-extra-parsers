package org.gridkit.jvmtool.jfr_jdk.parser;

import java.io.IOException;
import java.util.Collection;

import org.gridkit.jvmtool.spi.parsers.JsonEventSource;
import org.gridkit.jvmtool.util.json.JsonStreamWriter;

import jdk.jfr.consumer.RecordingFile;

class EventSource implements JsonEventSource {

    private final RecordingFile rec;
    private final JsonEventAdapter adapter;

    public EventSource(RecordingFile rec, int maxJsonDepth) {
        this.rec = rec;
        this.adapter = new JsonEventAdapter(maxJsonDepth);
    }

    public void setWhiteList(Collection<String> list) {
        adapter.setWhiteList(list);
    }

    public void setBlackList(Collection<String> list) {
        adapter.setBlackList(list);
    }

    @Override
    public boolean readNext(JsonStreamWriter writer) throws IOException {
        while(true) {
            if (!rec.hasMoreEvents()) {
                return false;
            }
            else {
                if (!adapter.encodeEvent(rec.readEvent(), writer)) {
                    continue;
                };
                return true;
            }
        }
    }
}
